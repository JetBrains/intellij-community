// "Unwrap '"blah"'" "false"
class X {

  @interface MyAnnotation {
    int value();
  }

  @MyAnnotation(value = {"blah"}<caret>)
  void foo() {}
}