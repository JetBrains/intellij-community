import java.io.IOException;

public class Foo {
    void m() {
        try {
            doAct()<caret>
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    void doAct() throws IOException  {}
}