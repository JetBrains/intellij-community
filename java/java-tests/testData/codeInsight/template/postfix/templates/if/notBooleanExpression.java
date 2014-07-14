package templates;

public class Foo {
    void m(String b, int value) {
        b.if<caret>
        value = 123;
    }
}