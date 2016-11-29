class DataFlowBug {

  void foo() {
    RuntimeException o = System.nanoTime() % 2 == 0 ? null : new RuntimeException();
    throw <warning descr="Dereference of 'o' may produce 'java.lang.NullPointerException'">o</warning>;
  }

  void foo2() {
    try {
      RuntimeException o = System.nanoTime() % 2 == 0 ? null : new RuntimeException();
      throw <warning descr="Dereference of 'o' may produce 'java.lang.NullPointerException'">o</warning>;
    }
    catch (RuntimeException exception) {

    }
  }

}