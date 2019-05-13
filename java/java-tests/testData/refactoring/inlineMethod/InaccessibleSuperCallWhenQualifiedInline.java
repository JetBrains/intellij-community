class A {
  void bar() {}
}

class B extends A {
  void foo() {
    super.bar();
  }

  static void err(B b) {
    b.fo<caret>o();
  }
}