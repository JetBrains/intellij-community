package templates;

public class Foo {
    void m(boolean b, int value) {
        b.if<caret>
        value = 123;
    }
}