public class Foo {
    public Foo(int x) { }
    void m() {
        Foo.new<caret>
        Bar a = new Bar();
    }
}