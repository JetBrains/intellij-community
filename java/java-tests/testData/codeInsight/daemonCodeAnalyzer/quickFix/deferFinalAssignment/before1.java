// "Defer assignment to 'n' using temp variable" "true"
import java.io.*;

class a {
    void f(InputStream in) {
        final int n;
        try {
            n = in.read();
        } catch (IOException e) {
            <caret>n = -1;
        }
        int y = 4;
    }
}

