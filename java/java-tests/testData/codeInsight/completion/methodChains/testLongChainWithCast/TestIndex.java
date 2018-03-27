class Main {
  void m(A a) {
    ((AImpl) a).getB().getC().getD();
    ((AImpl) a).getB().getC().getD();
    ((AImpl) a).getB().getC().getD();
    ((AImpl) a).getB().getC().getD();
    ((AImpl) a).getB().getC().getD();
    ((AImpl) a).getB().getC().getD();
    ((AImpl) a).getB().getC().getD();
    ((AImpl) a).getB().getC().getD();
    ((AImpl) a).getB().getC().getD();
    ((AImpl) a).getB().getC().getD();
    ((AImpl) a).getB().getC().getD();
    ((AImpl) a).getB().getC().getD();
    ((AImpl) a).getB().getC().getD();
    ((AImpl) a).getB().getC().getD();
  }
}

class A {

}

class AImpl extends A {
  B getB() {
    return new B();
  }
}

class B {
  C getC() {
    return new C();
  }
}

class C {
  D getD() {
    return new D();
  }
}

class D {

}
