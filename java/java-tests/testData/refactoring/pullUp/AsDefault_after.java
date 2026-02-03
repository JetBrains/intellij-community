interface Bar<T> { }

interface Base<T> {
    default void get(Bar<T> bar) { }
}

class Foo<T,U> implements Base<U> {
}