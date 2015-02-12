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
    m<error descr="Cannot resolve method 'm(<lambda expression>)'">(() -> {
      if (cond)
        return 42;
      else
        return foo();
    })</error>;

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
