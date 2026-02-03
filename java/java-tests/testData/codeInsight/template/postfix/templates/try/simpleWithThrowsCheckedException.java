import java.io.IOException;

public class Foo {
    void m() {
        doAct().try<caret>
    }

    void doAct() throws IOException  {}
}