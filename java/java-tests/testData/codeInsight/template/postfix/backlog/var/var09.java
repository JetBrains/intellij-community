public class Foo {
    void m(boolean b) {
        m(b && false.<caret>);
    }
}