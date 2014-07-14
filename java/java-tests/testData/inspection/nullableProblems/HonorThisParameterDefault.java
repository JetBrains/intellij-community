package foo;

interface A {
  void f(double a);
  void f2(Object a);
}

class B implements A {
  public void f(double a) {
  }
  public void f2(Object a) {
  }
}