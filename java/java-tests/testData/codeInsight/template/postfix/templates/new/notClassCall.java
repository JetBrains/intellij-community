public class Foo {
    void m() {
        m().new<caret>
    }
}