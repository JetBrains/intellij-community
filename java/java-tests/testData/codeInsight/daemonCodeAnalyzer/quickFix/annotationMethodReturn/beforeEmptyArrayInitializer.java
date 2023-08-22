// "Make 'value()' return 'java.lang.String[]'" "true-preview"
class X {
  @interface MyAnnotation {
    String value();
  }

  @MyAnnotation(value = {}<caret>)
  void foo() {}
}