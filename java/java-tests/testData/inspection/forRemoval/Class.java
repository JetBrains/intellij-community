@Deprecated(forRemoval = true)
class Test {
  public Test() {
  }
}

class Usages {
  void normal() {
    <error descr="'Test' is deprecated and marked for removal">Test</error> t =
      new <error descr="'Test' is deprecated and marked for removal">Test</error>();
  }

  @Deprecated()
  void normallyDeprecated() {
    <error descr="'Test' is deprecated and marked for removal">Test</error> t =
      new <error descr="'Test' is deprecated and marked for removal">Test</error>();
  }

  @Deprecated(forRemoval = 1 + 1 == 2)
  void deprecatedForRemoval() {
    <error descr="'Test' is deprecated and marked for removal">Test</error> t =
      new <error descr="'Test' is deprecated and marked for removal">Test</error>();
  }

  @SuppressWarnings("deprecation")
  void suppressDeprecation() {
    <error descr="'Test' is deprecated and marked for removal">Test</error> t =
      new <error descr="'Test' is deprecated and marked for removal">Test</error>();
  }

  @SuppressWarnings("removal")
  void suppressRemoval() {
    Test t =
      new Test();
  }
}