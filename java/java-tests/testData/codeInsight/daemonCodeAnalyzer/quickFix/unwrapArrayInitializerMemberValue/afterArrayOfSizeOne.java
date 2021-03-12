// "Replace array initializer with its element" "true"
class X {

  @interface MyAnnotation {
    String value();
  }

  @MyAnnotation(/*1*//*2*/value = "blah")
  void foo() {}
}