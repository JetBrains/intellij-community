// "Reuse previous variable 'i'" "false"
import java.io.*;

class a {
    int i;
    final int <caret>i;
    void f() {
        int h = 7;
    }
}

