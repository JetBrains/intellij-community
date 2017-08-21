public class Test {
  @Deprecated(forRemoval = true)
  void foo() {}
}

class Overrides extends Test {
  void foo() {
    super.foo();
  }
}

@SuppressWarnings("removal")
class Suppressed extends Test {
  void foo() {
    super.foo();
  }
}