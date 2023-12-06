import org.jetbrains.annotations.Nullable;

class Test {
  void test(@Nullable Object o) {
      String r = switch (o) {
          case String s when s.length() > 3 -> s.substring(0, 3);
          case Integer i -> "integer";
          case null, default -> "default";
      };
  }
}