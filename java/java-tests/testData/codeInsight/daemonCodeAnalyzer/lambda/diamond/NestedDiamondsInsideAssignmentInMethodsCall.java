import java.util.function.*;

class A {

    {
        B<Double> local;
        method(local = new B<>(new C<>((supplier) -> supplier.get())));
    }

    void method(B<?> value) {
    }

}

class B<T> {
    B(C<T> c) { }
}

class C<T> {
    C(Function<Supplier<T>, T> f) { }
}