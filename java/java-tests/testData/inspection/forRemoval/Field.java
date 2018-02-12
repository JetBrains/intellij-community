class Test {
  @Deprecated(forRemoval = true)
  int foo;
}

class Usages {
  private Test t;

  int normal() {
    return t.<error descr="'foo' is deprecated and marked for removal">foo</error>;
  }

  @Deprecated()
  int normallyDeprecated() {
    return t.<error descr="'foo' is deprecated and marked for removal">foo</error>;
  }

  @Deprecated(forRemoval = 1 + 1 == 2)
  int deprecatedForRemoval() {
    return t.<error descr="'foo' is deprecated and marked for removal">foo</error>;
  }

  @SuppressWarnings("deprecation")
  int suppressDeprecation() {
    return t.<error descr="'foo' is deprecated and marked for removal">foo</error>;
  }

  @SuppressWarnings("removal")
  int suppressRemoval() {
    return t.foo;
  }
}