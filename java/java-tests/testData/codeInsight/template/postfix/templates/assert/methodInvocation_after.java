package templates;

public class Foo {
    void m() {
        assert bar();<caret>
    }

    boolean bar() {
        return true;
    }
}