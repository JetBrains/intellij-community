import java.util.*;

class Test {
    interface Supplier<T> {
        T get();
    }

    interface Collector<T, A, R> {}
    <R, A> R collect(Collector<? super Integer, A, R> collector) {
        return null;
    }

    public static <T, C extends Collection<T>> Collector<T, ?, C> toCollection(Supplier<C> collectionFactory) {
        return null;
    }

    public static <T> Collector<T, ?, List<T>> toList() {
        return null;
    }

    void test() {
        List<Integer> l1 = collect(toList());
        List<Integer> l2 = collect(toCollection(ArrayList::new));
        m(collect(toList()));
        m(collect(toCollection(ArrayList::new)));
    }

    void m(List<Integer> l) { }
}
