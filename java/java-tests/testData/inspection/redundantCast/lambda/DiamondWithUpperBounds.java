class Foo<X> {
    void foo(Bar<X> bar) {}
    @SuppressWarnings("unchecked")
    void apply() {
        foo((Bar<X>) new Baz<>());
    }
}

class Bar<X> {}

class Baz<X extends Number> extends Bar<X> {} 