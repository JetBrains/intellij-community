public interface IDEA99969 {
  default IntStream distinct(Stream s) {
    return s.<error descr="Ambiguous method call: both 'Stream.map(Function)' and 'Stream.map(IntFunction)' match">map</error>(i -> <error descr="Inconvertible types; cannot cast '<lambda parameter>' to 'int'">(int) i</error>);
  }
}
interface Stream<T> {
  <R> Stream<R> map(Function<? super T, ? extends R> mapper);
  IntStream map(IntFunction<? super T> mapper);
  LongStream map(LongFunction<? super T> mapper);
}

interface Function<T, R> {
  public R apply(T t);
}

interface IntFunction<T> {
  public int applyAsInt(T t);
}

interface LongFunction<T> {
  public long applyAsLong(T t);
}


interface IntStream {}
interface LongStream {}
