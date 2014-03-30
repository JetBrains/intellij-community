abstract class Tmp<T> {
    private String concat(Tmp<String> tmp) {
        return tmp.collect(StringBuilder::new, StringBuilder::append, StringBuilder::append).toString();
    }

    abstract <R> R collect(Supplier<R> supplier,
                           BiConsumer<R, ? super T> accumulator,
                           BiConsumer<R, R> combiner);

    interface Supplier<T> {
        T get();
    }

    interface BiConsumer<T, U> {
        void accept(T t, U u);
    }
}
