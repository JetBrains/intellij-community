class s {
  void f() {
    <error descr="Cannot return a value from a method with void result type">return 0;</error>
  }
  void f2() {
    return;
  }

  int f3() {
    <error descr="Missing return value">return;</error>
  }
  int f4() {
    return 0;
  }

  {
    <error descr="Return outside method">return;</error>

  }
}