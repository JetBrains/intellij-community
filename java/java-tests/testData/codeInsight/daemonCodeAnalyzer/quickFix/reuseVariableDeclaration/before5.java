// "Reuse previous variable 'i'" "false"
import java.io.*;

class a {
    int i;
    void f() {
        final int <caret>i;
        int h = 7;
    }
}

