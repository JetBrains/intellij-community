class Foo {
    int foo;

    {
        Foo f;
        foo = f.foo;<caret>
    }
}