import java.util.List;
import java.util.Map;

class Collector<C> {
}

interface Function<T, R> {
   R apply(T t);
}

final class Collectors {
    public static <T>
    Collector<List<T>> toList() {
        return null;
    }


    public static <T1, K1> Collector<Map<K1, List<T1>>> groupingBy(Function<T1, K1> classifier) {
        return groupingBy(classifier, toList());
    }

    public static <T, K, D> Collector<Map<K, D>> groupingBy(Function<T, K> classifier,
                                                            Collector<D> downstream) {
        return null;
    }
}
