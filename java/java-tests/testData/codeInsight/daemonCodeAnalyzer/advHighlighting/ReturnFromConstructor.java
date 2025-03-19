class C {

  C() {
    <error descr="Cannot return a value from a constructor">return 1;</error>
  }

  <error descr="Invalid method declaration; return type required">x</error>() {
    return 1;
  }

  void y() {
    <error descr="Cannot return a value from a method with void result type">return 1;</error>
  }
}