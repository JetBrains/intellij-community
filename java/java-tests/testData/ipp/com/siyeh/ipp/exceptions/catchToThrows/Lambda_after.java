class Lambda {

  void bar() throws E {}

  void foo() {
    U u = () -> {

    };
  }

  interface U {
    void f() throws E;
  }
  class E extends Exception {}
}
