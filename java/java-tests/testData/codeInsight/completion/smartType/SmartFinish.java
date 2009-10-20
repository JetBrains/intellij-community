public class Foo {

    {
        Foo foo = null;
        Foo bar = id<caret>foo;
    }

    Foo id(Foo foo) {return foo;}

}
