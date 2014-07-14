public abstract class Foo {
    void m() {
        FooBar.new<caret>
    }
    class FooBar { private FooBar(int x) { } }
}