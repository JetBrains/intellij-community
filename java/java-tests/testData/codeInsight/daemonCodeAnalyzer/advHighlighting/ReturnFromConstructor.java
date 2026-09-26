class C {

  C() {
    <error descr="Cannot return a value from a constructor">return 1;</error>
  }

  <error descr="Method return type missing or constructor name does not match class name">x</error>() {
    return 1;
  }

  void y() {
    <error descr="Cannot return a value from a method with void result type">return 1;</error>
  }
}