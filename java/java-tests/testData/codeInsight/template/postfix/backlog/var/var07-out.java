public class Foo {
    Foo m() {
        Foo foo = m().m();
        foo<caret>.m();
        return null;
    }
}