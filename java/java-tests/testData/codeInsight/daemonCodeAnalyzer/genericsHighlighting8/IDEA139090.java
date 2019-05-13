interface I<T extends I<T>> {
    T foo();
}

class C {
    void baz(I<?> x) {
        bar(x.foo(), x);
    }

    <T> void bar(T x, T y) { }
}