// "Defer assignment to 'n' using temp variable" "true"
import java.io.*;

class a {
    void f(InputStream in) {
        final int n;
        if (in==null) {
            n= 2;
            <caret>n= 2;
        }
        else {
            n=4;
        }
        int p = 6;
    }
}

