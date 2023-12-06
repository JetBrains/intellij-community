class Lambda {

  void bar() throws E {}

  void foo() {
    U u = () -> {
      try {

      } <caret>catch (E e) {
        e.printStackTrace();
      }
    };
  }

  interface U {
    void f();
  }
  class E extends Exception {}
}
