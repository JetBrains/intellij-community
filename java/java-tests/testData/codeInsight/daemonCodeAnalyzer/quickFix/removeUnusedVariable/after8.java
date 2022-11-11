// "Remove local variable 'oo'" "true-preview"
import java.io.*;

class a {
    int k;
    private int run() {
        new a();

        return 0;
    }
}

