// "Make 'value()' return 'X.MyAnnotation3'" "true-preview"
class X {
  @interface MyAnnotation1 { }

  @interface MyAnnotation2 {
    MyAnnotation1 value();
  }

  @interface MyAnnotation3 { }

  @MyAnnotation2(value = @MyAnnotation3<caret>)
  void foo() {}
}