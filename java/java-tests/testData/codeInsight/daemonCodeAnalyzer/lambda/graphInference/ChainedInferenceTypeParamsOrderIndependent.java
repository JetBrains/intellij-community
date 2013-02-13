import java.util.*;

class Main {

    void test(List<Integer> li) {
       foo(li, s -> s.substr(0), Collections.emptyList());
       foo1(li, s -> s.substr(0), Collections.emptyList());
    }

    <It extends Iterable<U>, T, U> Collection<U> foo(Collection<T> coll, Fun<Stream<T>, Stream<U>> f, It it) {
        return null;
    }

    <T, It extends Iterable<U>, U> Collection<U> foo1(Collection<T> coll, Fun<Stream<T>, Stream<U>> f, It it) {
        return null;
    }

    interface Stream<T> {
        Stream<T> substr(long startingOffset);
    }

    interface Fun<T, R> {
        R _(T t);
    }
}

