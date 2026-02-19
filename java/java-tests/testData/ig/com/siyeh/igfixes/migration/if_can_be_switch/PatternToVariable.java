import org.jetbrains.annotations.Nullable;

class Test {
  void test(@Nullable Object o) {
      String r;
      <caret>if (o instanceof String s && s.length() > 3) {
          r = s.substring(0, 3);
      } else if (o instanceof Integer) {
          r = "integer";
      } else {
          r = "default";
      }
  }
}