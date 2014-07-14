public class Foo {
    void m(boolean b) {
        boolean foo = b && false;
        m(foo<caret>);
    }
}