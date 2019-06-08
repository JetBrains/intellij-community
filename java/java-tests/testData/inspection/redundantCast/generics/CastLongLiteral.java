class A {
  void method() {
    int v = (int)(<warning descr="Casting '1L' to 'long' is redundant">long</warning>)1L;
  }
}