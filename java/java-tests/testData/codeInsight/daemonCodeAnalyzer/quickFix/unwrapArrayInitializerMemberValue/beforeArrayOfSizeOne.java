// "Unwrap '"blah"'" "true"
class X {

  @interface MyAnnotation {
    String value();
  }

  @MyAnnotation(value = {/*1*/"blah"/*2*/}<caret>)
  void foo() {}
}