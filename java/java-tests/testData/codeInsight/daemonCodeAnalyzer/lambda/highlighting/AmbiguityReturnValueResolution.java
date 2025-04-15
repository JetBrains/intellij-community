public interface IDEA99969 {
  default IntStream distinct(Stream s) {
    return s.map<error descr="Ambiguous method call: both 'Stream.map(Function)' and 'Stream.map(IntFunction)' match">(i -> (int) i)</error>;
  }
  default IntStream distinct2(Stream<String> s) {
    return s.map<error descr="Ambiguous method call: both 'Stream.map(Function<? super String, ?>)' and 'Stream.map(IntFunction<? super String>)' match">(i -> <error descr="Inconvertible types; cannot cast 'java.lang.String' to 'int'">(int) i</error>)</error>;
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
