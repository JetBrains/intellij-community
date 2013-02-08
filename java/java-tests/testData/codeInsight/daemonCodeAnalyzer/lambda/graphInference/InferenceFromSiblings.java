import java.util.*;

class Main {

    void test(List<Integer> li) {
        Fun<Stream<Integer>, Stream<Integer>> f = s -> s.substr(0);
        foo(li, f, Collections.emptyList());

        foo(li, s -> s.substr(0), Collections.emptyList());
    }

    <T, U, S_OUT extends Stream<U>, It extends Iterable<U>> Collection<U>
            foo(Collection<T> coll, Fun<Stream<T>, S_OUT> f, It it) {
        return null;
    }

    interface Stream<T> {
        Stream<T> substr(long startingOffset);
    }

    interface Fun<T, R> {
        R _(T t);
    }
}