interface A {
  void f();
}

interface B {
  void f();
}

class C implements A, B {
  @Override
  void f<caret>() {
  }
}