package templates;

public class Foo {
    void m(boolean b, int value) {
        assert b;<caret>
        value = 123;
    }
}