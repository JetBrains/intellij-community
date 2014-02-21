class Super {
  void foo() {
    Super s = new Super();
    s.bar();
  }

  void bar() {}
}