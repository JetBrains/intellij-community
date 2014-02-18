import java.util.Map;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.stream.Collector;
import java.util.stream.Stream;

class Bug
{

  void foo(Stream<Bug> words){
    words.collect(toMap(w -> 1, (a, b) -> a + b));
  }

  public static <T, K, U>
  Collector<T, ?, Map<K,U>> toMap(Function<Bug,  U> valueMapper,
                                  BinaryOperator<U> mergeFunction) {
    return null;
  }
}
