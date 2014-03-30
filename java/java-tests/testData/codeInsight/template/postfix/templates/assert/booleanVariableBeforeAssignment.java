package templates;

public class Foo {
    void m(boolean b, int value) {
        b.assert<caret>
        value = 123;
    }
}