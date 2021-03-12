// "Replace array initializer with its element" "false"
class X {

  @interface MyAnnotation {
    String value();
  }

  @MyAnnotation(value = {"one", "two"}<caret>)
  void foo() {}
}