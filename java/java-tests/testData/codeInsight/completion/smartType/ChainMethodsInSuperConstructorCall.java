class A {
  A(String a) {}
}

class B extends A {

  B(Object aab) {
    super(aab.to<caret>);
  }
}