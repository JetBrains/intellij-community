class Test {
  @Deprecated(forRemoval = true)
  void foo() {}
}

class Overrides extends Test {
  void <error descr="Overrides method that is deprecated and marked for removal in 'Test'">foo</error>() {
    super.<error descr="'foo()' is deprecated and marked for removal">foo</error>();
  }
}

@SuppressWarnings("removal")
class Suppressed extends Test {
  void foo() {
    super.foo();
  }
}