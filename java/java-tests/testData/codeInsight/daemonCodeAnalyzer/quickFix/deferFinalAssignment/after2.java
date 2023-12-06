// "Defer assignment to 'n' using temp variable" "true-preview"
import java.io.*;

class a {
    void f(InputStream in) {
        final int n;
        int <caret>n1;
        if (in==null) {
            n1 = 2;
            n1 = 2;
        }
        else {
            n1 =4;
        }
        n = n1;
        int p = 6;
    }
}

