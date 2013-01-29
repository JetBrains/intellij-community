public interface IDEA99969 {
  default IntStream distinct(Stream s) {
    return s.map(i -> (int) i);
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
