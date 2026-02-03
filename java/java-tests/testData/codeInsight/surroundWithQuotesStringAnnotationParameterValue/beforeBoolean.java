// "Surround annotation parameter value with quotes" "true"
class X {

  @interface MyAnnotation {
    String value();
  }

  @MyAnnotation(tr<caret>ue)
  void m() {

  }

}
