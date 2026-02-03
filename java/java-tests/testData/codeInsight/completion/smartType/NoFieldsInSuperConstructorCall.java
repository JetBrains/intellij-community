class A {
  A(int a) {}
}

class B extends A {
  int aaa;

  B(int aab) {
    super(a<caret>);
  }
}