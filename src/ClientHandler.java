import java.io.*;
import java.net.Socket;

class ClientHandler implements Runnable {
    private final Socket clientSocket;
    private final Server server;

    public ClientHandler(Socket clientSocket, Server server) {
        this.clientSocket = clientSocket;
        this.server = server;
    }

    @Override
    public void run() {
        try (
                BufferedReader reader = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(clientSocket.getOutputStream()))
        ) {
            writer.write("Please provide your username:");
            writer.newLine();
            writer.flush();

            String clientName = reader.readLine();
            if (clientName == null || clientName.trim().isEmpty()) {
                // giving them default username
                clientName = "Client-" + clientSocket.getPort();
            }

            server.addClient(clientName, clientSocket);
            server.broadcastMessage(clientName + " has joined the chat.", clientSocket);

            writer.write("Connected clients: " + String.join(", ", server.getClientNames()));
            writer.newLine();
            writer.write("Instructions:");
            writer.newLine();
            writer.write("1. Type your message to broadcast it to everyone.");
            writer.newLine();
            writer.write("2. Use '/send <username1,username2> <message>' to send to specific users.");
            writer.newLine();
            writer.write("3. Use '/exclude <username1,username2> <message>' to broadcast excluding specific users.");
            writer.newLine();
            writer.write("4. Use '/banned' to query banned phrases.");
            writer.newLine();
            writer.write("5. Type 'exit' to disconnect.");
            writer.newLine();
            writer.flush();

            String message;
            while ((message = reader.readLine()) != null) {
                if (message.trim().isEmpty()) {
                    sendMessage("Cannot send an empty message. Please type something.");
                    continue;
                }

                if (message.equalsIgnoreCase("exit")) {
                    server.broadcastMessage(clientName + " has left the chat.", clientSocket);
                    break;
                }

                if (message.equalsIgnoreCase("/banned")) {
                    sendMessage("Banned phrases: " + String.join(", ", server.getBannedPhrases()));
                    continue;
                }

                if (message.startsWith("/send")) {
                    String[] parts = message.split(" ", 3);

                    String[] recipients = parts[1].split(",");
                    String userMessage = parts[2];

                    if (server.getBannedPhrases().stream().anyMatch(userMessage.toLowerCase()::contains)) {
                        sendMessage("Your message contains a banned phrase and will not be sent.");
                        continue;
                    }

                    for (String recipient : recipients) {
                        recipient = recipient.trim(); // Ensure whitespace is trimmed
                        boolean success = server.sendMessageToUser(clientName + " (private): " + userMessage, recipient);
                        if (!success) {
                            sendMessage("User " + recipient + " not found. Available clients: " + String.join(", ", server.getClientNames()));
                        }
                    }
                    continue;
                }

                if (message.startsWith("/exclude")) {
                    String[] parts = message.split(" ", 3);
                    String[] excludedUsers = parts[1].split(",");
                    String userMessage = parts[2];

                    if (server.getBannedPhrases().stream().anyMatch(userMessage.toLowerCase()::contains)) {
                        sendMessage("Your message contains a banned phrase and will not be broadcast.");
                        continue;
                    }

                    server.broadcastMessageExcluding(clientName + ": " + userMessage, clientSocket, excludedUsers);
                    continue;
                }

                if (server.getBannedPhrases().stream().anyMatch(message.toLowerCase()::contains)) {
                    sendMessage("Your message contains a banned phrase and will not be broadcast.");
                } else {
                    server.broadcastMessage(clientName + ": " + message, clientSocket);
                }
            }
        } catch (IOException e) {
            System.err.println("Error handling client: " + e.getMessage());
        } finally {
            server.removeClient(clientSocket);
        }
    }
    public void sendMessage(String message) throws IOException {
        BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(clientSocket.getOutputStream()));
        writer.write(message);
        writer.newLine();
        writer.flush();
    }
}