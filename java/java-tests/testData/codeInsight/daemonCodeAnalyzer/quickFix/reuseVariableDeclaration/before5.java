// "Reuse previous variable 'i' declaration" "false"
import java.io.*;

class a {
    int i;
    void f() {
        final int <caret>i;
        int h = 7;
    }
}

