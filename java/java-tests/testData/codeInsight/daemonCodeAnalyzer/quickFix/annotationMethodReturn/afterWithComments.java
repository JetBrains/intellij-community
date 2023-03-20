// "Make 'value()' return 'int'" "true-preview"
class X {
  @interface MyAnnotation {
    /*1*/int/*2*/ /*3*/value(/*4*/)/*5*/;
  }

  @MyAnnotation(value = 42)
  void foo() {}
}