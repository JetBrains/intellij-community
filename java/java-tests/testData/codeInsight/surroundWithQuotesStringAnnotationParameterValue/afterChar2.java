// "Surround annotation parameter value with quotes" "true"
class X {

  @interface MyAnnotation {
    String value();
  }

  @MyAnnotation(value= "\n")
  void m() {

  }

}