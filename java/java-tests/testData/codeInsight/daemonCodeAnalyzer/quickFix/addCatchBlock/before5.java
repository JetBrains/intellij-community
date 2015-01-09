// "Add 'catch' clause(s)" "true"
import java.io.IOException;

class Test {
    static class MyResource implements AutoCloseable {
        public void close() throws IOException { }
    }

    void m() {
        try (<caret>MyResource r = new MyResource()) {
        }
    }
}