package templates;

public class Foo {
    void m() {
        bar().assert<caret>
    }

    boolean bar() {
        return true;
    }
}