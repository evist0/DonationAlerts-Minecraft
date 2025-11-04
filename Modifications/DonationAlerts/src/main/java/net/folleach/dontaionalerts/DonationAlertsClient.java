package net.folleach.dontaionalerts;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.socket.client.IO;
import io.socket.client.Socket;
import io.socket.emitter.Emitter;
import net.folleach.daintegrate.listeners.IListener;
import org.json.JSONException;
import org.json.JSONObject;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashSet;
import java.util.Set;

public class DonationAlertsClient {
    private final Set<Integer> processedIds = new HashSet<>();
    private static final int MAX_PROCESSED_IDS = 1000; // Ограничиваем размер множества

    private static final Gson gson = new GsonBuilder()
            .setDateFormat("yyyy-MM-dd HH:mm:ss")
            .create();

    private final Socket socket;
    private String currentToken;

    public DonationAlertsClient(String server, IListener<DonationAlertsEvent> eventListener) throws URISyntaxException {
        System.out.print("New DonationAlertsClient");

        URI url = new URI(server);

        socket = IO.socket(url);

        Emitter.Listener connectListener = arg -> System.out.println("connect");

        Emitter.Listener disconnectListener = arg -> System.out.println("disconnect");

        Emitter.Listener donationListener = arg -> {
            if (arg.length < 1) return;
            var json = arg[0];
            if (!(json instanceof String jsonString)) return;

            System.out.println("Received event: " + jsonString);

            DonationAlertsEvent event = gson.fromJson(jsonString, DonationAlertsEvent.class);

            if (processedIds.contains(event.ID)) {
                System.out.println("🔄 Skipping duplicate event ID: " + event.ID);
                return;
            }

            if (processedIds.size() > MAX_PROCESSED_IDS) {
                processedIds.clear();
                System.out.println("🧹 Cleaned processed IDs cache");
            }

            processedIds.add(event.ID);

            System.out.println("✅ Processing event ID: " + event.ID);

            eventListener.onValue(event);
        };

        Emitter.Listener errorListener = arg -> System.out.println("error");

        socket.on(Socket.EVENT_CONNECT, connectListener)
                .on(Socket.EVENT_DISCONNECT, disconnectListener)
                .on(Socket.EVENT_ERROR, errorListener)
                .on("donation", donationListener);
    }

    public boolean connect(String token) {
        currentToken = token;
        socket.connect();
        try {
            socket.emit("add-user", new JSONObject()
                    .put("token", currentToken)
                    .put("type", "minor"));
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
        return true;
    }

    public void disconnect() {
        socket.close();
        currentToken = null;
    }

    public boolean getConnected() {
        return socket != null && socket.connected();
    }
}
