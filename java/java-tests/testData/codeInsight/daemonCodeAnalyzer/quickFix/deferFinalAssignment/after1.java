// "Defer assignment to 'n' using temp variable" "true-preview"
import java.io.*;

class a {
    void f(InputStream in) {
        final int n;
        int <caret>n1;
        try {
            n1 = in.read();
        } catch (IOException e) {
            n1 = -1;
        }
        n = n1;
        int y = 4;
    }
}

