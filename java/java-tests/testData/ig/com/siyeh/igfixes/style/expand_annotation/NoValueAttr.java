class T {
  @interface A {
    String size();
    String type();
  }

  @A(<caret>"a")
  void foo() {
  }
}