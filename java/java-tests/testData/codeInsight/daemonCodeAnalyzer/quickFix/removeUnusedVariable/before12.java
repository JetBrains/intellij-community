// "Remove local variable 'o1'" "true-preview"
import java.io.*;

class a {
    int k;

    private int run() {
        Object <caret>o1, /*ddddd*/ oo = (Object) new Integer(0);
        return 0;
    }
}