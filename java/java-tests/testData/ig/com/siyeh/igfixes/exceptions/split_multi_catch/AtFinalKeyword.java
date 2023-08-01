import java.io.IOException;
import java.net.*;

public class AtFinalKeyword {
    void f() {
        try {
            throw new NoRouteToHostException();
        } catch (fin<caret>al NoRouteToHostException | SocketException | IOException e) {
            e.printStackTrace();
        }
    }
}