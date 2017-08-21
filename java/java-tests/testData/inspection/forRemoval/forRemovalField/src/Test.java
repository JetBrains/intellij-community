public class Test {
  @Deprecated(forRemoval = true)
  int foo;

  int normal() {
    return foo;
  }

  @Deprecated()
  int normallyDeprecated() {
    return foo;
  }

  @Deprecated(forRemoval = 1 + 1 == 2)
  int deprecatedForRemoval() {
    return foo;
  }

  @SuppressWarnings("deprecation")
  int suppressDeprecation() {
    return foo;
  }

  @SuppressWarnings("removal")
  int suppressRemoval() {
    return foo;
  }
}