class Test {
  @Deprecated(forRemoval = true)
  void foo() {}
}

class Usages {
  private Test t;

  void normal() {
    t.<error descr="'foo()' is deprecated and marked for removal">foo</error>();
  }

  @Deprecated()
  void normallyDeprecated() {
    t.<error descr="'foo()' is deprecated and marked for removal">foo</error>();
  }

  @Deprecated(forRemoval = 1 + 1 == 2)
  void deprecatedForRemoval() {
    t.<error descr="'foo()' is deprecated and marked for removal">foo</error>();
  }

  @SuppressWarnings("deprecation")
  void suppressDeprecation() {
    t.<error descr="'foo()' is deprecated and marked for removal">foo</error>();
  }

  @SuppressWarnings("removal")
  void suppressRemoval() {
    t.foo();
  }
}