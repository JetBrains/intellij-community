// "Make 'value()' return 'java.lang.String'" "true-preview"
class X {
  @interface MyAnnotation {
    String value() default<caret>;
  }

  @MyAnnotation(value = "blah")
  void foo() {}
}