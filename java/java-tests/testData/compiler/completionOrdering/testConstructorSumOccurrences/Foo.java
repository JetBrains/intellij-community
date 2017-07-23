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

abstract class A {}

class B extends A {
  B() {}
  B(int i) {}
  B(int i, int j) {}
}

class C extends A {
}