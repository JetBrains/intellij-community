import java.util.Collection;
import java.util.Set;

class FooObject<T> {}
class FooId<T extends FooObject> {}

interface Bar {
    <T extends FooObject, I extends FooId<? extends T>> T get(I key);
    <T extends FooObject, I extends FooId<? extends T>> Collection<T> get(Collection<I> keys);
}

class Target {
    void foo(Bar bar) {
        final Set<FooId<?>> keys = null;
        final Collection<FooObject> values = bar.get(keys);
    }
}