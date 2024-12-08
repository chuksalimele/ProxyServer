package chuks.flatbook.proxy;

import java.io.*;
import java.net.*;
import java.util.*;

/**
 * Proxy server for forwarding connections. Handles disconnection
 * scenarios between client and remote server.
 */
public class ProxyServer {

    private static final String CONFIG_FILE = "fix_proxy_config.properties";

    public static void main(String[] args) {
        Properties config = loadConfig();

        for (String localPort : config.stringPropertyNames()) {
            String remoteMapping = config.getProperty(localPort);
            String[] remoteDetails = remoteMapping.split(":");
            if (remoteDetails.length != 2) {
                System.err.println("Invalid mapping for port " + localPort);
                continue;
            }
            String remoteHost = remoteDetails[0];
            int remotePort = Integer.parseInt(remoteDetails[1]);
            int localPortNumber = Integer.parseInt(localPort);

            new Thread(() -> startProxy(localPortNumber, remoteHost, remotePort)).start();
        }
    }

    private static Properties loadConfig() {
        Properties config = new Properties();
        URL url = ProxyServer.class.getClassLoader().getResource(CONFIG_FILE);

        try (InputStream input = new FileInputStream(url.getFile())) {
            config.load(input);
        } catch (IOException e) {
            System.err.println("Failed to load config file: " + e.getMessage());
            System.exit(1);
        }
        return config;
    }

    private static void startProxy(int localPort, String remoteHost, int remotePort) {
        try (ServerSocket serverSocket = new ServerSocket(localPort)) {
            System.out.println("Proxying on localhost:" + localPort + " to " + remoteHost + ":" + remotePort);
            while (true) {
                Socket clientSocket = serverSocket.accept();
                new Thread(() -> handleConnection(clientSocket, remoteHost, remotePort)).start();
            }
        } catch (IOException e) {
            System.err.println("Error on port " + localPort + ": " + e.getMessage());
        }
    }

    private static void handleConnection(Socket clientSocket, String remoteHost, int remotePort) {
        final Socket[] remoteSocket = {null}; // Use an array to wrap the remoteSocket reference
        try {
            remoteSocket[0] = new Socket(remoteHost, remotePort);
            System.out.println("Connected to remote server: " + remoteHost + ":" + remotePort);

            Thread clientToRemote = new Thread(() -> forwardData(clientSocket, remoteSocket[0]));
            Thread remoteToClient = new Thread(() -> forwardData(remoteSocket[0], clientSocket));
            clientToRemote.start();
            remoteToClient.start();

            clientToRemote.join();
            remoteToClient.join();
        } catch (IOException e) {
            System.err.println("Failed to connect to remote server: " + e.getMessage());
        } catch (InterruptedException e) {
            System.err.println("Thread interrupted: " + e.getMessage());
        } finally {
            closeSocket(remoteSocket[0]); // Access the first element of the array
            closeSocket(clientSocket);
        }
    }

    private static void forwardData(Socket inputSocket, Socket outputSocket) {
        try (InputStream in = inputSocket.getInputStream(); OutputStream out = outputSocket.getOutputStream()) {
            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = in.read(buffer)) != -1) {
                out.write(buffer, 0, bytesRead);
                out.flush();
            }
        } catch (IOException e) {
            System.err.println("Connection lost: " + e.getMessage());
            closeSocket(inputSocket);
            closeSocket(outputSocket);
        }
    }

    private static void closeSocket(Socket socket) {
        if (socket != null && !socket.isClosed()) {
            try {
                socket.close();
            } catch (IOException e) {
                System.err.println("Failed to close socket: " + e.getMessage());
            }
        }
    }
}
