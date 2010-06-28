// "Defer assignment to 'i' using temp variable" "true"
import java.io.*;

class a {
    void f(int k) {
        final int i;
        i = 4;
        <caret>i = 4;
        f(i);
    }
}

