
class Test {

    {
        Foo<Integer> f = new <caret>
    }
}

class Foo<T> {
    public Foo(String description) {}
    public Foo(int size) {}
}
