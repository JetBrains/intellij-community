import org.jetbrains.annotations.Nullable;

class RemovePatternNullabilityAnnotation {
  record Rec(String value) {}

  @Nullable String field;

  void test(Object o) {
    if (o instanceof Rec(String s)) {
      System.out.println(s);
    }
  }
}
