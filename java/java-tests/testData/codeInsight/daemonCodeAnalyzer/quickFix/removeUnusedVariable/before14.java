// "Remove local variable 'o1'" "true-preview"
import java.io.*;

class a {
    int k;
    private int run() {
        Object o0, oo, <caret>o1;

        return 0;
    }
}

