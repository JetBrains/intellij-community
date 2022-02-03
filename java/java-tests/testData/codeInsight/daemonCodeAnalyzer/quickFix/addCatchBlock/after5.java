// "Add 'catch' clause(s)" "true"
import java.io.IOException;

class Test {
    static class MyResource implements AutoCloseable {
        public void close() throws IOException { }
    }

    void m() {
        try (MyResource r = new MyResource()) {
        } catch (IOException e) {
            <selection>throw new RuntimeException(e);</selection>
        }
    }
}