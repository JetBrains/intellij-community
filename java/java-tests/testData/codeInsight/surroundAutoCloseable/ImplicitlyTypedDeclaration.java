import java.io.IOException;
import java.io.InputStream;
import java.net.URLConnection;

class C {
    public void read(URLConnection connection) throws IOException {
        <caret>InputStream stream = connection.getInputStream();
        var available = stream.available();
        System.out.println(available);
    }
}
