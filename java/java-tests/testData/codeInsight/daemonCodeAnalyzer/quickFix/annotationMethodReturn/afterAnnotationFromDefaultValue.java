// "Make 'value()' return 'X.MyAnnotation1'" "true-preview"
class X {
  @interface MyAnnotation1 { }

  @interface MyAnnotation2 {
    MyAnnotation1 value() default @MyAnnotation1;
  }
}