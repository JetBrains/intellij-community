import java.util.stream.Collectors;
import java.util.stream.Stream;

// "Fold expression into Stream chain" "true-preview"
class Test {
  String foo(String a, String b, String c, String d) {
    return Stream.of(a, b, c, d).map(String::trim).collect(Collectors.joining());
  }
}