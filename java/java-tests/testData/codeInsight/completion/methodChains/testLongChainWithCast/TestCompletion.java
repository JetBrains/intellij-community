class Main {
  void m() {
    D d = <caret>
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
