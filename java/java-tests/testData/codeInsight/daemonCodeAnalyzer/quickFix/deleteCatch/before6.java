// "Delete catch for 'java.io.IOException'" "true"
import java.io.*;

class a {
    void f(boolean f) {
        if (f)
            try {
                System.out.println();
                System.out.println();
            } catch (<caret>IOException e) {
            }
    }
}
