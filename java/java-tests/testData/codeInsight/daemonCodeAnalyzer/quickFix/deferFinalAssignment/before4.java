// "Defer assignment to 'n' using temp variable" "true"
import java.io.*;

class a {
    final int n;
    a(InputStream in) {
        if (in==null) {
            n= 2;
            <caret>n= 2;
            int h = n;
        }
        else {
            n=4;
        }
        f(n);
        int p = n;
    }
    void f(int i) {}
}

