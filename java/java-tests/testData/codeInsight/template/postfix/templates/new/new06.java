public class Foo {
    public Foo() { }
    void m() {
        Foo.new<caret>
        _a = new Bar();
    }
}