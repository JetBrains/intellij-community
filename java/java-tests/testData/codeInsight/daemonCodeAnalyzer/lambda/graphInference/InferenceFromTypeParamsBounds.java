import java.util.Map;

class IDEA10166 {
    interface BiConsumer<T, U> {
        void accept(T t, U u);
    }

    interface BinaryOperator<T> extends BiFunction<T, T, T> {
    }

    interface BiFunction<T, U, R> {
        R apply(T t, U u);
    }

    interface Supplier<T> {
        public T get();
    }

    public static <T, U, M1 extends Map<T, U>> Collector<T, M1> joiningWith(BinaryOperator<U> mergeFunction,
                                                                            Supplier<M1> mapSupplier,
                                                                            BiConsumer<M1, T> accumulator) {
        BinaryOperator<M1> mapBinaryOperator = leftMapMerger(mergeFunction);
        return new CollectorImpl<>(mapSupplier, accumulator, leftMapMerger(mergeFunction));
    }

    static <K, V, M2 extends Map<K, V>> BinaryOperator<M2> leftMapMerger(BinaryOperator<V> mergeFunction) {
        return null;
    }


    interface Collector<T, R> {
    }

    static class CollectorImpl<T, R> implements Collector<T, R> {
        CollectorImpl(Supplier<R> resultSupplier, BiConsumer<R, T> accumulator, BinaryOperator<R> combiner) {
        }
    }
}