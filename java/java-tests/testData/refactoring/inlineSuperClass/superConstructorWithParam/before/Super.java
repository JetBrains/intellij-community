class Super {
  String s;
  Super(String s) {
    if (s != null) {
      this.s = s;
    }
  }

  void foo() {
    Super s = new Super(null);
    s.bar();
  }

  void bar() {}
}