import java.util.function.Function;
import java.util.function.ToIntFunction;
import java.util.function.ToLongFunction;
import java.util.stream.IntStream;
import java.util.stream.LongStream;

interface Test {
  default IntStream distinct2(Stream<String> s) {
    return s.map<error descr="Ambiguous method call: both 'Stream.map(Function<? super String, ?>)' and 'Stream.map(ToIntFunction<? super String>)' match">(i -> i.length())</error>;
  }
}

interface Stream<T> {
  <R> Stream<R> map(Function<? super T, ? extends R> mapper);
  IntStream map(ToIntFunction<? super T> mapper);
  LongStream map(ToLongFunction<? super T> mapper);
}