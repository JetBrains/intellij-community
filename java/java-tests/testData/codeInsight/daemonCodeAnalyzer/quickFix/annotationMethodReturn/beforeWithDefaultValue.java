// "Make 'value()' return 'java.lang.String'" "true-preview"
class X {
  @interface MyAnnotation {
    int value() default 42;
  }

  @MyAnnotation(value = "blah"<caret>)
  void foo() {}
}