import java.lang.RuntimeException;

public class Foo {
    void m() {
        new RuntimeException("error");.throw    <caret>
    }
}