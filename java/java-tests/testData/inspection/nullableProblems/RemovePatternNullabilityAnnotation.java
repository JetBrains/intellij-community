import org.jetbrains.annotations.Nullable;

class RemovePatternNullabilityAnnotation {
  record Rec(String value) {}

  @Nullable String field;

  void test(Object o) {
    if (o instanceof Rec(<warning descr="Nullability annotation is not applicable to pattern types">@Nul<caret>lable</warning> String s)) {
      System.out.println(s);
    }
  }
}
