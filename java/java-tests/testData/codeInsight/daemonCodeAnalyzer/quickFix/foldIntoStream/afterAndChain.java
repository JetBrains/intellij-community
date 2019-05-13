import java.util.stream.Stream;

// "Fold expression into Stream chain" "true"
class Test {
  boolean foo(String a, String b, String c, String d) {
    return Stream.of(a, b, c, d).allMatch(s -> s.startsWith("xyz"));
  }
}