public class Foo {
    Foo[] xs;
    void m() {
        if (xs.length != 0<caret>) {

        }
    }
}