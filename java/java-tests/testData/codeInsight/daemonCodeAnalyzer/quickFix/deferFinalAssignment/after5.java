// "Defer assignment to 'i' using temp variable" "true-preview"
import java.io.*;

class a {
    void f(int k) {
        final int i;
        int <caret>i1;
        i1 = 4;
        i1 = 4;
        i = i1;
        f(i);
    }
}

