import java.util.ArrayList;
import java.util.List;

class Collector<C> {
}

interface S<T> {

    /**
     * Gets a result.
     *
     * @return a result
     */
    T get();
}


interface F<TF> {}

final class Collectors {
    public static <T>
    Collector<List<T>> toList() {
        return null;
    }


    public static <K1> Collector<ArrayList<K1>> groupingBy(F<K1> classifier) {
        return groupingBy(classifier, ArrayList ::new);
    }

    public static <K, M extends ArrayList<K>>
    Collector<M> groupingBy(F<K> classifier,
                            S<M> mapFactory) {
        return null;
    }
}
