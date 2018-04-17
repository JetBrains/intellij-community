import java.util.stream.Collectors;
import java.util.stream.IntStream;

// "Fold expression into Stream chain" "true"
class Test {
  String foo(int a, int b, int c, int d) {
    return IntStream.of(a, b, c, d).map(i -> i * 2).mapToObj(String::valueOf).collect(Collectors.joining("|"));
  }
}