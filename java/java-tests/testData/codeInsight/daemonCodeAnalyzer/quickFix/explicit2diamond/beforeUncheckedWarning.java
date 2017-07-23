// "Replace with <>" "false"
class Foo<Z> {
    void foo(final Bar baz) {
        Z z =  z(new Bar<St<caret>ring>(baz));
    }

    <P> Bar<P> c(Bar<P> b) {
        return b;
    }

    private <X> Z z(Bar<X> b) {
        return null;
    }
}

class Bar<T> {
    public Bar(Bar<T> v) {
    }
}