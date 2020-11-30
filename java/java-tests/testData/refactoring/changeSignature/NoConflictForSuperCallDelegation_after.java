class A {
  void foo() {}
}

class B extends A {
  void foo() {
    super.foo();
  }
}