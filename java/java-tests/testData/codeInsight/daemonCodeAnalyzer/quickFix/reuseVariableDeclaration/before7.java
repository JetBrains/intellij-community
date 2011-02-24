// "Reuse previous variable 'r' declaration" "true"
import java.io.*;

class a {
    static class MyResource implements AutoCloseable {
        public void close() { }
    }

    void m() {
        MyResource r;
        try (MyResource <caret>r = new MyResource()) {
        }
    }
}
