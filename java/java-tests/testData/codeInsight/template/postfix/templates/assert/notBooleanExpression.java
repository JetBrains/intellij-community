package templates;

public class Foo {
    void m(String b, int value) {
        b.assert<caret>
        value = 123;
    }
}