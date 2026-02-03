import java.util.*;

abstract class TypeTest {

    interface I {}

    public Collection<? extends I> excludeFrom(Collection<? extends I> include, Collection<? extends I> exclude) {
        return copyOf(filter(include, not(in(exclude))));
    }

    interface Predicate<T> {
        boolean apply(T t);
    }

    abstract <T> Predicate<T> in(Collection<? extends T> target);
    abstract <T> Predicate<T> not(Predicate<T> aPredicate);
    abstract <E> List<E> copyOf(Iterable<? extends E> elements);

    abstract <T> Iterable<T> filter(Iterable<T> unfiltered, Predicate<? super T> predicate);
}
