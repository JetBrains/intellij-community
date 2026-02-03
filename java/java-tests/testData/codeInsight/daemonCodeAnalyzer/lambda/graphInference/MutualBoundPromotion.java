
import java.util.Collection;
import java.util.List;

interface Mappable<T> {
    T map(Mapper mapper);
    class Foo<B extends Foo.FooBuilder<B, U>, U extends Foo<B, U>> implements Mappable<Foo<B, U>> {
        static class FooBuilder<B extends FooBuilder<B, U>, U extends Foo<B, U>> {}
        @Override
        public Foo<B, U> map(Mapper mapper) { return this; }
    }

    class Mapper {
        <E extends Mappable<? extends E>> List<E> map(List<E> in) { return in; }
        <E extends Mappable<? extends E>> Collection<E> map(Collection<E> in) { return in; }
    }
    class Counter implements Mappable<Counter> {
        private final List<Foo<?, ?>> in;
        Counter(List<Foo<?, ?>> in) { this.in = in; }
        @Override
        public Counter map(Mapper mapper) {
            return new Counter(mapper.map(in));
        }
    }
}