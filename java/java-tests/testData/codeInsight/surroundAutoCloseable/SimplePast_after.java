import java.io.*;
import java.net.*;

class C {
    public void read(URLConnection connection) {
        try (InputStream stream = connection.getInputStream()) {<caret>
        }
    }
}