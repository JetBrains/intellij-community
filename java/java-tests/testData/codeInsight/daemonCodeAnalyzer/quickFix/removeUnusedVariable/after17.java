// "Remove local variable 'o'" "true-preview"
import java.io.*;

class a {
    int k;
    private int run() {
        int a = 0;<caret>

        return a;
    }
}

