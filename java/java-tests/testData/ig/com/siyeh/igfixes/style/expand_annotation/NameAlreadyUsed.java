class T {
  @interface A {
    String value();
    String type();
  }

  @A(<caret>"a", value="z")
  void foo() {
  }
}