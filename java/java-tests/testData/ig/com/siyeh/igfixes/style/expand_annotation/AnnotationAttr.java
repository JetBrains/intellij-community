class T {
  @interface B { String[] value(); }
  @interface C { B value(); }

  @C(<caret>@B(value = "v"))
  void foo() {
  }
}