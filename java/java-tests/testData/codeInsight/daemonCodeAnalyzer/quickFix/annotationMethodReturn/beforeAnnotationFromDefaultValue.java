// "Make 'value()' return 'X.MyAnnotation1'" "true-preview"
class X {
  @interface MyAnnotation1 { }

  @interface MyAnnotation2 {
    int value() default @MyAnnotation1<caret>;
  }
}