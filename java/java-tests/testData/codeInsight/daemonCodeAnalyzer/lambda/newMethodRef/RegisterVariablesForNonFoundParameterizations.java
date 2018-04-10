import java.util.stream.Stream;
import java.util.Optional;

class SomeClass<T> {

    private T id;

    public T getId() {
        return id;
    }

    public void n(final Optional<SomeClass<T>> o) {
        T otherId = o.map(SomeClass::getId).orElse(null);
    }

}

class A {
    interface Base<T> {
        T foo();
    }

    interface Sub<S> extends Base<S> { }

    void m(Stream<Sub> stream) {
        stream.map( Sub::foo);
    }
}