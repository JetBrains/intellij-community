import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

class Main {
  void test(Object value) {
    Map<Integer, String> collect = Stream.of("1", "2", "3")
                                     .collect(Collectors.to<caret>UnmodifiableMap(Integer::parseInt, xx -> xx));
  }
}
