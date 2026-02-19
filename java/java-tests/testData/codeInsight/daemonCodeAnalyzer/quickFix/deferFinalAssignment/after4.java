// "Defer assignment to 'n' using temp variable" "true-preview"
import java.io.*;

class a {
    final int n;
    a(InputStream in) {
        int <caret>n1;
        if (in==null) {
            n1 = 2;
            n1 = 2;
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

