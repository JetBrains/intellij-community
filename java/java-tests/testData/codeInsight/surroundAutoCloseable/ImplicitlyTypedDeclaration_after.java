import java.io.IOException;
import java.io.InputStream;
import java.net.URLConnection;

class C {
    public void read(URLConnection connection) throws IOException {
        int available;
        try (InputStream stream = connection.getInputStream()) {
            available = stream.available();
        }
        System.out.println(available);
    }
}
