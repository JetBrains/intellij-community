class A {
  void <caret>first() {
    second();
  }

  void <caret>second() {
    first();
  }

  void kept() {}
}
