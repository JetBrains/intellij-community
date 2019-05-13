import java.util.List;

abstract class CollectionsExamples<T> {
    private  List<T> getEvenL() {
        return collect( toList());
    }

    static <TT> Collector<?, List<TT>> toList() {
        return null;
    }

    abstract <R, A> R collect(Collector<A, R> collector);

    interface Collector<A, R> {}
}
