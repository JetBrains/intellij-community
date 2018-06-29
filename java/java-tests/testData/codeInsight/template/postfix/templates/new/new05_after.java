public class Foo {
    public Foo(int x) { }
    void m() {
        new Foo(<caret>)
        Bar a = new Bar();
    }
}