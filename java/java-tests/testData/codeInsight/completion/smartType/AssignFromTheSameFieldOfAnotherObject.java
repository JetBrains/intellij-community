class Foo {
    int foo;

    {
        Foo f;
        foo = f.f<caret>
    }
}