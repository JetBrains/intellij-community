// "Reuse previous variable 'i'" "true-preview"
import java.io.*;

class a {
    void f(int i) {
        final int <caret>i;
        int h = 7;
    }
}

