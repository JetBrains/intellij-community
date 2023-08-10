public class Foo<A> {
    public Foo(Foo<A> f) {
    }

    Foo<Foo<A>> foos() {
        return new Foo<Foo<A>>(new Foo<Foo<A>>(null));
    }
} 