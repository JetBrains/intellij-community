class DataFlowBug {

  void foo() {
    RuntimeException o = System.nanoTime() % 2 == 0 ? null : new RuntimeException();
    throw <warning descr="Dereference of 'o' may produce 'NullPointerException'">o</warning>;
  }

  void foo2() {
    try {
      RuntimeException o = System.nanoTime() % 2 == 0 ? null : new RuntimeException();
      throw <warning descr="Dereference of 'o' may produce 'NullPointerException'">o</warning>;
    }
    catch (RuntimeException exception) {

    }
  }

  void foo(RuntimeException tt) {
    throw tt;
  }

}