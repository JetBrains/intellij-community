class A {
  void m() {
    class Local {}
    n();
  }
  void n() {}
}

class B extends A {
  void n() {}
}

class C extends B {
  {
    <caret>m();
  }
}