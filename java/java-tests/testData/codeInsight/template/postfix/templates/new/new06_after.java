public class Foo {
    public Foo() { }
    void m() {
        new Foo()<caret>;
        _a = new Bar();
    }
}