package Server;
import DTO.*;
import com.mongodb.BasicDBObject;
import org.apache.log4j.Logger;

import static spark.Spark.*;
import DAO.MessagesDAO;
import DAO.ProfileDAO;
import DTO.ProfileListDTO;
import DTO.UpdateProfileDTO;
import com.google.gson.Gson;
import com.mongodb.MongoClient;
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.MongoDatabase;
import static com.mongodb.client.model.Filters.*;
import org.bson.Document;
import org.bson.conversions.Bson;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class Server {
  private static List<String> items = new ArrayList<>();

  public static void main(String[] args){
    // Open connection
    MongoClient mongoClient = new MongoClient("localhost", 27017);
    // Get ref to database
    MongoDatabase db = mongoClient.getDatabase("FinalDatabase");
    // Get ref to collection
    MongoCollection<Document> messageCollection = db.getCollection("Messages");
    MongoCollection<Document> userCollection = db.getCollection("Users");

    /* Not really needed, comment out for now
    // Adds a second initial message to db if it's not already there
    List<Document> initialMessage2 = messageCollection.find(new Document("user", "User Name 2"))
            .into(new ArrayList<>());

    if (initialMessage2.isEmpty()) {
      Document newMessage = new Document()
              .append("text", "The quick brown fox jumps over the lazy dog")
              .append("user", "User Name 2")
              .append("date", "May 5, 2020, 10:25:43 PM");

      messageCollection.insertOne(newMessage);
    }
     */

    // Check if initial user is in database, if not then add
    List<Document> initialUser1 = userCollection.find(new Document("username", "user"))
      .into(new ArrayList<>());

    if (initialUser1.isEmpty()) {
      Document newUser = new Document()
        .append("username", "user")
        .append ("password", "password")
        .append("profilePic", 1);

      userCollection.insertOne(newUser);
    }

    // Init Gson
    Gson gson = new Gson();

    port(1234);

    // 2 way communication
    webSocket("/ws", WebSocketHandler.class); // open socket and leave it open
    get("/hello", (req, res) -> "hi"); // test

    post("api/authenticate",(req, res) -> {
      String bodyString = req.body();
        AuthDTO authDTO = gson.fromJson(bodyString, AuthDTO.class);

      List<Document> user = userCollection.find(new Document("username", authDTO.username))
        .into(new ArrayList<>());
      if(user.size() != 1) {
          AuthResponseDTO responseDTO = new AuthResponseDTO(false, "User not found");
        return gson.toJson(responseDTO);
      }

      Document userDocument = user.get(0);
      if(!userDocument.getString("password").equals(authDTO.password)) {
          AuthResponseDTO responseDTO = new AuthResponseDTO(false, "Password is incorrect");
        return gson.toJson(responseDTO);
      }

        AuthResponseDTO responseDTO = new AuthResponseDTO(true, null);
      return gson.toJson(responseDTO);

    });

    post("api/register", (req, res) -> {
      String bodyString = req.body();
        AuthDTO authDTO = gson.fromJson(bodyString, AuthDTO.class);

      List<Document> user = userCollection.find(new Document("username", authDTO.username))
        .into(new ArrayList<>());

      if(!user.isEmpty()) {
          AuthResponseDTO authResponseDTO = new AuthResponseDTO(false, "User already exists");
        return gson.toJson(authResponseDTO);
      }

      Random rand = new Random();
      int profilePicNum = rand.nextInt(4);

      Document newUser = new Document()
        .append("username", authDTO.username)
        .append("password", authDTO.password)
        .append("profilePic", profilePicNum);
      userCollection.insertOne(newUser);
        AuthResponseDTO authResponseDTO = new AuthResponseDTO(true, null);

      return gson.toJson(authResponseDTO);
    });

    post("/api/addMessage", (req, res) -> {
      String bodyString = req.body();
      AddMessageDTO messageDTO = gson.fromJson(bodyString,
        AddMessageDTO.class);
      // Add it to the list
      MessagesDAO messagesDAO = MessagesDAO.getInstance();
      messagesDAO.addMessage(messageDTO.text, messageDTO.user);
      System.out.println(bodyString);
      return "OK";
    });

    post("/api/profilePic", (req, res) -> {
      String bodyString = req.body();
      Document userToFind = userCollection.find(eq("username", bodyString)).first();
      assert userToFind != null;
      return userToFind.get("profilePic").toString();
    });

    get("/api/getAllMessages", (req, res) -> {
      MessagesDAO messagesDAO = MessagesDAO.getInstance();
      MessagesListDTO list = messagesDAO.getAllMessages();
      return gson.toJson(list);
    });

    post("/api/updateProfile", (req, res) -> {
      //this one is quite strange to me, much to work on
      String bodyString = req.body();
      AuthDTO authDTO = gson.fromJson(bodyString, AuthDTO.class);
      ProfileDTO profileDTO = gson.fromJson(bodyString,ProfileDTO.class);

      Document userfound = userCollection.find(new Document("username", authDTO.username))
              .first();

      if(userfound != null){
        Bson updatedvalue = new Document("username",profileDTO.Newname);
        Bson updateopt = new Document("$set", updatedvalue);
        userCollection.updateOne(userfound, updateopt);

        Bson updatedvalue2 = new Document("password",profileDTO.Newpassword);
        Bson updateopt2 = new Document("$set", updatedvalue2);
        userCollection.updateOne(userfound, updateopt2);
      }
    });

    post("/api/deleteUser", (req, res) -> {
      String bodyString = req.body();
      AuthDTO authDTO = gson.fromJson(bodyString, AuthDTO.class);

      List<Document> user = userCollection.find(new Document("username", authDTO.username))
              .into(new ArrayList<>());

      if(!user.isEmpty()) {
        BasicDBObject theQuery = new BasicDBObject();
        theQuery.put("username", authDTO.username);
        userCollection.deleteMany(theQuery);
      }

    });

    get("/api/getProfile", (req, res) -> {

      FindIterable<Document> fi = userCollection.find();
      MongoCursor<Document> cursor = fi.iterator();
      try {
        while(cursor.hasNext()) {
          return gson.toJson(cursor.next());
        }
      } finally {
        cursor.close();
      }
    });

    /* Can be modified to delete users:

    post("/api/deleteItem", (req, res) -> {
      String bodyString = req.body();
      AddItemDTO itemDTO = gson.fromJson(bodyString,
              AddItemDTO.class);
      // Delete it from the list
      ItemsDAO itemsDAO = ItemsDAO.getInstance();
      itemsDAO.deleteItem(itemDTO.item, itemDTO.price);
      System.out.println(bodyString);
      System.out.println(items.size());
      return "OK";
    });
     */
  }
}
