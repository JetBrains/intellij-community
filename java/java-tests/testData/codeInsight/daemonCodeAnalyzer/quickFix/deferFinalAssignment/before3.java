// "Defer assignment to 'n' using temp variable" "true"
import java.io.*;

class a {
    void f(InputStream in) {
        final int n;
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

