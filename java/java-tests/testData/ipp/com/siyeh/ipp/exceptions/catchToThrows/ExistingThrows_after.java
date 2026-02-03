abstract class C {
  static class E1 extends Exception { }

  abstract void f() throws E1;

  void m() throws E1 {
      f();
  }
}