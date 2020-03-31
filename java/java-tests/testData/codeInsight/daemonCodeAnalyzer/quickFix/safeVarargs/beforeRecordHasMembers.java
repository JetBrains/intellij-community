// "Annotate as @SafeVarargs" "true"
public record T<caret>est(java.util.List<String>... args) {
  static final String FOO = "bar";
  
  void test() {}
}

