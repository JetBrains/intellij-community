public class Foo {
    Foo f;
    Foo m() {
        Foo foo = m().m().f;<caret>
        blah();
        return null;
    }
}