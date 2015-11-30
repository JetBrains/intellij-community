class Test {
  interface GetInt { int get(); }
  interface GetInteger { Integer get(); }

  private void <warning descr="Private method 'm(Test.GetInt)' is never used">m</warning>(GetInt getter) {
    System.out.println(getter);
  }

  private void m(GetInteger getter) {
    System.out.println(getter);
  }

  void test(boolean cond) {
    <error descr="Ambiguous method call: both 'Test.m(GetInt)' and 'Test.m(GetInteger)' match">m</error>(() -> {
      if (cond)
        return 42;
      else
        return foo();
    });

    m(() -> {
      return foo();
    });

    m(() -> {
      if (cond)
        return new Integer(42);
      else
        return foo();
    });

  }

  private <T> T foo() {
    return null;
  }
}
