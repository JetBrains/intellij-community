// "Remove local variable 'oo'" "true-preview"
import java.io.*;

class a {
    int k;
    private int run() {
        Object o1 = /**fffff*/ this, /*ddddd*/<caret>oo = (Object) new Integer(0);

        return 0;
    }
}

