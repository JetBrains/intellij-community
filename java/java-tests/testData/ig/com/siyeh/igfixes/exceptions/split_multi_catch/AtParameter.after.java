import java.io.IOException;
import java.net.*;

public class AtParameter {
    void f() {
        try {
            throw new NoRouteToHostException();
        } catch (NoRouteToHostException e) {
            e.printStackTrace();
        } catch (SocketException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}