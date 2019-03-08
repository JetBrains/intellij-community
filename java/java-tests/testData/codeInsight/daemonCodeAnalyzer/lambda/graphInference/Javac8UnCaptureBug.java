import java.util.function.Predicate;

class B<T> {
    public static <E> B<E> from( Iterable<? extends E> iterable) {
        return null;
    }

    void m(Iterable<? extends T> it, Predicate<? super T> p) {
        <error descr="Incompatible types. Found: 'B<capture<? extends T>>', required: 'B<T>'">B<T> B = from(it).bar(not(p));</error>
    }

    B<T> bar(Predicate<? super T> p) {
        return this;
    }

    public static <N> Predicate<N> not(Predicate<? super N> c) {
        return null;
    }
}