// "Annotate as '@SafeVarargs'" "true-preview"
public record Test(java.util.List<String>... args) {
  static final String FOO = "bar";

    @SafeVarargs
    public Test {
    }

    void test() {}
}

