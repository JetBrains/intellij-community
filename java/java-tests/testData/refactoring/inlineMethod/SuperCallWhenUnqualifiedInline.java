class A {
  void bar() {}
}

class B extends A {
  void foo() {
    super.bar();
  }

  void err() {
    fo<caret>o();
  }
}