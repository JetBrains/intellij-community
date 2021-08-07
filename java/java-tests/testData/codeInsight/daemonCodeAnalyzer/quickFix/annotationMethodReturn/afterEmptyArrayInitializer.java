// "Make 'value' return 'java.lang.String[]'" "true"
class X {
  @interface MyAnnotation {
    String[] value();
  }

  @MyAnnotation(value = {})
  void foo() {}
}