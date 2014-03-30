import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collector;
import java.util.stream.Stream;
import java.util.*;

class X1 {
  void test(Stream<Integer> stream) {
    Function<List<Integer>, List<Integer>> unmodifiableList = Collections::unmodifiableList;
    stream.collect(collectingAndThen(toList(), unmodifiableList)).remove(0);
  }

  public static<T,A1,R,RR> Collector<T,A1,RR> collectingAndThen(Collector<T,A1,R> downstream,
                                                                Function<R,RR> finisher) {
    return null;
  }

  static <T> Collector<T, ?, List<T>> toList() {
    return null;
  }
}
