// "Defer assignment to 'n' using temp variable" "true"
import java.io.*;

class a {
    void f(InputStream in) {
        final int n;
        int n1;
        if (in==null) {
            n1 = 2;
            <caret>n1 = 2;
        }
        else {
            n1 =4;
        }
        n = n1;
        int p = 6;
    }
}

