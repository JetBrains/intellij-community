// "Reuse previous variable 'i'" "false"
import java.io.*;

class a {
    void f(int i) {
        final short <caret>i;
        int h = 7;
    }
}

