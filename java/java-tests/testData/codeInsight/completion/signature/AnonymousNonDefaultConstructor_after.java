abstract class Foo{
    public Foo(int x) {
    }

    abstract int foo();

    {
        Foo f = new Foo(<selection>x</selection><caret>) {}
    }
}