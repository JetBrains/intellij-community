import java.util.*;

public class ConcurrentCollectors {
    static class Test1 {
        static <T, K, D, M1 extends ConcurrentMap<K, D>> C<T, M1> groupingBy(F<M1> f,
                                                                             C<T, D> c,
                                                                             BiConsumer<M1, T> consumer) {
            return new CImpl<>(f, consumer, arg(c.getOp()));
        }

        static <K, V, M2 extends Map<K, V>> BiOp<M2> arg(BiOp<V> op) {
            return null;
        }
    }

    static class Test2 {
        static <T, K, D, M1 extends ConcurrentMap<K, D>> C<T, M1> groupingBy(F<M1> f,
                                                                             C<T, D> c,
                                                                             BiConsumer<M1, T> consumer) {
            return new CImpl<>(f, consumer, arg(c.getOp()));
        }

        static <K, V, M2 extends ConcurrentMap<K, V>> BiOp<M2> arg(BiOp<V> op) {
            return null;
        }
    }

    static class Test3 {
        static <T, K, D, M1 extends Map<K, D>> C<T, M1> groupingBy(F<M1> f,
                                                                   C<T, D> c,
                                                                   BiConsumer<M1, T> consumer) {
            return new CImpl<<error descr="Cannot infer arguments"></error>>(f, consumer, arg(c.getOp()));
        }

        static <K, V, M2 extends ConcurrentMap<K, V>> BiOp<M2> arg(BiOp<V> op) {
            return null;
        }
    }


    interface C<T, R> {
        BiOp<R> getOp();
    }

    interface F<T> {}


    static class CImpl<T, R> implements C<T, R> {
        CImpl(F<R> f,
              BiConsumer<R, T> consumer,
              BiOp<R> op) {
        }

        @Override
        public BiOp<R> getOp() {
            return null;
        }
    }

    interface BiFun<T, U, R> { }

    interface BiOp<T> extends BiFun<T, T, T> {
    }

    interface BiConsumer<T, U> {}
    
    interface ConcurrentMap<A, B> extends Map<A, B> {}
}
