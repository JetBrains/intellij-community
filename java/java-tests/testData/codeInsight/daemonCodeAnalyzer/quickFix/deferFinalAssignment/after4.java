// "Defer assignment to 'n' using temp variable" "true"
import java.io.*;

class a {
    final int n;
    a(InputStream in) {
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

