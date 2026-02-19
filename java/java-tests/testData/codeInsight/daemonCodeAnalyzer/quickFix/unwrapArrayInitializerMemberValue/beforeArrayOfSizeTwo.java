// "Unwrap '"one"'" "false"
class X {

  @interface MyAnnotation {
    String value();
  }

  @MyAnnotation(value = {"one", "two"}<caret>)
  void foo() {}
}