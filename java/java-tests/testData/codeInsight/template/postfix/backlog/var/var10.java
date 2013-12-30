public class Foo<T> {
    void m() {
        Foo<Foo>.<caret>;
    }
}