interface Bar<T> { }

interface Base<T> {
    void foo(Bar<T> bar);
}

class Foo<T,U> implements Base<U> {
    @Override
    public void foo(Bar<U> bar) { }
}