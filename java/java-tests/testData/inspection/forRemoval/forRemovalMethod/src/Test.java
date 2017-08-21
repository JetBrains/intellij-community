public class Test {
  @Deprecated(forRemoval = true)
  void foo() {}

  void normal() {
    foo();
  }

  @Deprecated()
  void normallyDeprecated() {
    foo();
  }

  @Deprecated(forRemoval = 1 + 1 == 2)
  void deprecatedForRemoval() {
    foo();
  }

  @SuppressWarnings("deprecation")
  void suppressDeprecation() {
    foo();
  }

  @SuppressWarnings("removal")
  void suppressRemoval() {
    foo();
  }
}