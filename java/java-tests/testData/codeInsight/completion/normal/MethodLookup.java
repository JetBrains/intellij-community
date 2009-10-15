class B {
    void setA(int i) {}
    void setB(int i) {}
  }

  class C {
    B b;
  }

  class A {

  C c;

  A() {
    c.b.set<caret>A();
  }
}