// "Replace array initializer with its element" "false"
class X {

  @interface MyAnnotation {
    String value();
  }

  @MyAnnotation(value = {}<caret>)
  void foo() {}
}