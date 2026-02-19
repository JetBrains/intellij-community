// "Remove local variable 'oo'" "true-preview"
import java.io.*;

class a {
    boolean k = new File("1.tmp").delete();
    private int run() {
        Object <caret>oo = (Object) new a();

        return 0;
    }
}

