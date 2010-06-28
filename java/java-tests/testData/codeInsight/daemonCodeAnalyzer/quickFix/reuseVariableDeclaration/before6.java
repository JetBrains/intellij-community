// "Reuse previous variable 'i' declaration" "false"
import java.io.*;

class a {
    int i;
    final int <caret>i;
    void f() {
        int h = 7;
    }
}

