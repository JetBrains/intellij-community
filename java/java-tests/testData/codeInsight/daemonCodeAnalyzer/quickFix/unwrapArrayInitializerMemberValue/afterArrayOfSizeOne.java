// "Unwrap '"blah"'" "true-preview"
class X {

  @interface MyAnnotation {
    String value();
  }

  @MyAnnotation(/*1*//*2*/value = "blah")
  void foo() {}
}