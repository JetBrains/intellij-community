class Bar {}
class Foo {
    static Bar bar;
}
class C {
    {
        Bar expr = Foo.bar;
        Bar b = expr;
    }
}