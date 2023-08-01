class Yield {
  class X {
    void test() {
      <warning descr="Unqualified call to 'yield' method is not supported in releases since Java 14">yield</warning>("x");
      <warning descr="Unqualified call to 'yield' method is not supported in releases since Java 14">yield</warning>(1);
    }
  }

  void test() {
    <warning descr="Unqualified call to 'yield' method is not supported in releases since Java 14">yield</warning>("x");
    <warning descr="Unqualified call to 'yield' method is not supported in releases since Java 14">yield</warning>(1);
  }
  
  void varYield() {
    int yield = 5;
    yield++;
    yield = 7;
  }

  void yield(int x) {}

  static void yield(String x) {
  }
  
  class <warning descr="Use of 'yield' as a class name is not supported in releases since Java 14">yield</warning> {}
  class foo<<warning descr="Use of 'yield' as a class name is not supported in releases since Java 14">yield</warning>> {}
  static <<warning descr="Use of 'yield' as a class name is not supported in releases since Java 14">yield</warning>> void foo() {}
}
