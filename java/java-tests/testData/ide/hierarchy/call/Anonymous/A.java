abstract class A {
  A(int a) {}

  abstract void g();

  void h() {
    f();
  }

  void f() {
    A x = new A(42) {
      void g() {}
    };
  }
}