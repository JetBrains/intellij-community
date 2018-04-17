import java.util.stream.Stream;

// "Fold expression into Stream chain" "true"
class Test {
  int foo(String a, String b, String c, String d) {
    return Stream.of(a, b, c, d).mapToInt(String::length).sum();
  }
}