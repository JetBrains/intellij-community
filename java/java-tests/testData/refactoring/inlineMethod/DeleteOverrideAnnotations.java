class A {
  void f<caret>oo() {}
}

class B extends A {
  @Override
  void foo() {}

  void err() {
    super.foo();
  }
}

class C extends B {
  @Override
  void foo() {}
}