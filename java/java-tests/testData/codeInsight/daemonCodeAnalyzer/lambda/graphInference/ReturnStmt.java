import java.util.*;
class Main {

    <T, R> Collector<T, R> m(Supplier<? extends R> supplier, BiConsumer<R, T> accumulator) {
        return null;
    }

    <T, C extends Collection<T>> Collector<T, C> test1(Supplier<C> collectionFactory) {
        return m(collectionFactory, Collection::add);
    }

    Collector<String, StringBuilder> test2(Supplier<StringBuilder> sb) {
        return m(sb, StringBuilder::append);
    }

    interface Supplier<T> {
        public T get();
    }

    interface Collector<T, R> {
    }

    interface BiConsumer<T, U> {
        void accept(T t, U u);
    }

}