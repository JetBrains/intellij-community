public abstract class Foo<T, U> {
    void m() {
        Foo<Integer, U>.new<caret>
    }
}