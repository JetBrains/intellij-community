public abstract class Foo {
    void m() {
        new FooBar(<caret>)
    }
    class FooBar { private FooBar(int x) { } }
}