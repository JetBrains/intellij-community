interface A {
  void x();
}

interface B {
  void x();
}

class C implements A, B {
  @Override
  void x() {
  }
}