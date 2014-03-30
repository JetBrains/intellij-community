package templates;

public class Foo {
    void m(boolean b, int value) {
        if (b) {
            <caret>
        }
        value = 123;
    }
}