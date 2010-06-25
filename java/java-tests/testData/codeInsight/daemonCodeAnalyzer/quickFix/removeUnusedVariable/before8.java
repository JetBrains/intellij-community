// "Remove variable 'oo'" "true"
import java.io.*;

class a {
    int k;
    private int run() {
        Object <caret>oo = (Object) new a();

        return 0;
    }
}

