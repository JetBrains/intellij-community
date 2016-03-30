import java.io.*;
import java.net.*;

class C {
    public void read(URLConnection connection) {
        InputStream stream = connection.getInputStream();<caret>
    }
}