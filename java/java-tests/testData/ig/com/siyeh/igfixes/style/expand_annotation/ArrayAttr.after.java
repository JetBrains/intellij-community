class T {
  @interface A {
    String[] value();
  }

  @A(value = {"a"})
  void bar() {
  }
}