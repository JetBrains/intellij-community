class Foo {
  void m() {
    new C();
    new C();

    new B();
    new B(0);
    new B(0, 0);

    <caret>
  }
}

interface A {}

class B implements A {
  B() {}
  B(int i) {}
  B(int i, int j) {}
}

class C implements A {
}