import java.util.function.Predicate;

class B<T> {
    public static <E> B<E> from( Iterable<? extends E> iterable) {
        return null;
    }

    void m(Iterable<? extends T> it, Predicate<? super T> p) {
        B<T> B = from(it).<error descr="Incompatible types. Found: 'B<capture<? extends T>>', required: 'B<T>'">bar</error>(not(p));
    }

    B<T> bar(Predicate<? super T> p) {
        return this;
    }

    public static <N> Predicate<N> not(Predicate<? super N> c) {
        return null;
    }
}