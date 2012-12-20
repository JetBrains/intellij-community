class X {
    public static void main(final Stream<String> stream) throws Throwable {
        stream.map(s -> s.substring("http://".length())).forEach(System.out::println);
    }
}

interface Stream<T> {
    <R> Stream<R> map(Function<? super T, ? extends R> mapper);
    IntStream map(IntFunction<? super T> mapper);
    void forEach(Block<? super T> block);
}

interface IntFunction<T> extends Function<T, Integer> {
    public int applyAsInt(T t);
}

interface Function<T, R> {
    public R apply(T t);
}

interface IntStream extends BaseStream<Integer> {}
interface BaseStream<T> {}
interface Block<T> {
    public void accept(T t);
}
