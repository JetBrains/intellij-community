// "Reuse previous variable 'k' declaration" "true"
import java.io.*;

class a {
    void f(int i) {
        final int k = 234/5+7;
        int h = 7;
        int <caret>k = 234/5+7;
    }
}

