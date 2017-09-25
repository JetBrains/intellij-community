
class Test {

    {
        Foo<Integer> f = new Foo<>();
    }
}

class Foo<T> {
    public Foo(String description) {}
    public Foo(int size) {}
}
