class IDEA101168<E> {

    public void foo(Stream<OfPrimitive<E>> stream, Collector<CharSequence, Double> collector) {
        stream.map(Object ::toString).collect(collector);
    }

    interface OfPrimitive<A> {
    }

    interface Collector<T, R> {
        interface OfInt<R> extends Collector<Integer, R> {
        }
    }

    interface Stream<T> extends BaseStream<T> {
        <R> Stream<R> map(Function<? super T, ? extends R> mapper);
       IntStream map(ToIntFunction<? super T> mapper);
        <R> R collect(Collector<? super T, R> collector);
    }

    interface Function<T, R> {
        public R apply(T t);
    }

    interface ToIntFunction<T> {
        public int applyAsInt(T t);
    }

    interface IntStream extends BaseStream<Integer> {
        <R> R collect(Collector.OfInt<R> collector);
    }
    interface BaseStream<T> {}
}

