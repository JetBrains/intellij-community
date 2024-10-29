import java.util.Map;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.stream.Stream;

class Main {
  void test(Object value) {
      Collector<String, ?, Map<Integer, String>> unmodifiableMap = Collectors.toUnmodifiableMap(Integer::parseInt, xx -> xx);
      Map<Integer, String> collect = Stream.of("1", "2", "3")
                                     .collect(unmodifiableMap);
  }
}
