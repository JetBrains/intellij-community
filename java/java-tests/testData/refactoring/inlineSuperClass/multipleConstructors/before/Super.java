class Super {
  Super(String s){}

  void foo() {
    Super s = new Super("");
    s.bar();
  }

  void bar() {}
}