import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collector;
import java.util.stream.Stream;

class MultiDataPoint {
  public MultiDataPoint(final Map<String, String> pCollect) {}

  public static void convertValueResults(final Stream<Result> pStream) {
    map(() -> new MultiDataPoint(collect(toMap(r -> r.event.substring(0)))));
  }

  static <R> R collect(Collector<? super Result, ?, R> collector) {return null;}

  static <R> R map(Supplier<R> s) {
    return null;
  }

  static <T, K> Collector<T, ?, Map<K,K>> toMap(Function< T,  K> keyMapper) {
    return null;
  }

  static class Result {
    public String event;

    public String getValue() {
      return null;
    }
  }
}

