// "Remove local variable 'o'" "true-preview"
import java.io.*;

class a {
    int k;
    private int run() {
        Object[] <caret>o = new Object[] { null };

        return 0;
    }
}

