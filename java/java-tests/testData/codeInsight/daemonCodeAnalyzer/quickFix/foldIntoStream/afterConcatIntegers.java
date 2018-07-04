import java.util.stream.Collectors;
import java.util.stream.Stream;

// "Fold expression into Stream chain" "true"
class Test {
  void test2(Integer a, Integer b, Integer c, Integer d) {
    String result = Stream.of(a, b, c, d).map(String::valueOf).collect(Collectors.joining(",")) + ",";
  }
}