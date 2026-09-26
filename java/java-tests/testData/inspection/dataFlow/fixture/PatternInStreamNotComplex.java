import java.util.stream.Stream;

class Aaa {
  private static void test(String str) {
    Stream.of(str)
      .flatMap(Aaa::stream)
      .forEach(expr -> {
        if (value(expr) instanceof Integer i) {
          System.out.println(expr);
        }
      });
  }

  private static Object value(String expr) {
    return expr.trim();
  }

  private static Stream<String> stream(String str) {
    return Stream.of(str);
  }
}