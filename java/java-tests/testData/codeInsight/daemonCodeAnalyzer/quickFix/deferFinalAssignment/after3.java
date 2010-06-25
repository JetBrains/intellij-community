// "Defer assignment to 'n' using temp variable" "true"
import java.io.*;

class a {
    void f(InputStream in) {
        final int n;
        int n1;
        if (in==null) {
            n1 = 2;
            <caret>n1 = 2;
            int h = n1;
        }
        else {
            n1 =4;
        }
        n = n1;
        f(n);
        int p = n;
    }
    void f(int i) {}
}

