public class Foo<T> {
    void m() {
        Foo<Integer>.new<caret>
    }
}