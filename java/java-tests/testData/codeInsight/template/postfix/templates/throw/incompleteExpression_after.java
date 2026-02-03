import java.lang.Exception;

public class Foo {
    void m() {
        methodCall(.throw   <caret>
    }

    Exception methodCall(String s) {
        return null;
    }
}