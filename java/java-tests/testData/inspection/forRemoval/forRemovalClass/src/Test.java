@Deprecated(forRemoval = true)
class Test {
  public Test() {
  }
}

class Usages {
  void normal() {
    Test t =
      new Test();
  }

  @Deprecated()
  void normallyDeprecated() {
    Test t =
      new Test();
  }

  @Deprecated(forRemoval = 1 + 1 == 2)
  void deprecatedForRemoval() {
    Test t =
      new Test();
  }

  @SuppressWarnings("deprecation")
  void suppressDeprecation() {
    Test t =
      new Test();
  }

  @SuppressWarnings("removal")
  void suppressRemoval() {
    Test t =
      new Test();
  }
}