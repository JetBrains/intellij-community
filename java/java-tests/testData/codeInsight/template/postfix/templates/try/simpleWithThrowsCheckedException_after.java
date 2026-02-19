import java.io.IOException;

public class Foo {
    void m() {
        try {
            doAct()<caret>
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    void doAct() throws IOException  {}
}