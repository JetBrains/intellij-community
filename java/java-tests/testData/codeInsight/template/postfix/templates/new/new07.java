public class Foo {
    void m() {
        IFoo.new<caret>
        f();
    }
}

interface IFoo {

}