public class Foo {
    void m(boolean b) {

        b.assert<caret>
        value = null;
    }
}