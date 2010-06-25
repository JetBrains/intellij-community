// "Reuse previous variable 'i' declaration" "true"
import java.io.*;

class a {
    void f(int i) {
        int <caret>i = 234/5+7;
    }
}

