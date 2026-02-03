// "Remove local variable 'o'" "true-preview"
import java.io.*;

class a {
    int k;
    private int run() {
        int a = 0;
        Object[] <caret>o = new Object[1];

        return a;
    }
}

