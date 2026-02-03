public class Foo {

    {
        Foo foo = null;
        Foo bar = id(foo);<caret>
    }

    Foo id(Foo foo) {return foo;}

}
