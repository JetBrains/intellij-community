import java.io.IOException;
import java.net.*;

public class AtFinalKeyword {
    void f() {
        try {
            throw new NoRouteToHostException();
        } catch (final NoRouteToHostException e) {
            e.printStackTrace();
        } catch (final SocketException e) {
            e.printStackTrace();
        } catch (final IOException e) {
            e.printStackTrace();
        }
    }
}