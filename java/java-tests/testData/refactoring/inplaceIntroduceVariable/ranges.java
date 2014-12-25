class Bar {}
class Foo {
    static Bar bar;
}
class C {
    {
        Bar b = Foo.ba<caret>r;
    }
}