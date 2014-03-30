package templates;

public class Foo {
    void m() {
        if (bar()) {
            <caret>
        }
    }

    boolean bar() {
        return true;
    }
}