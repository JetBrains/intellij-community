// "Surround annotation parameter value with quotes" "true"
class X {

  @interface MyAnnotation {
    String value();
  }

  @MyAnnotation("true")
  void m() {

  }

}
