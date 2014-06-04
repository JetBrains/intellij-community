// "Surround annotation parameter value with quotes" "false"
class X {

  @interface MyAnnotation {
    String c();
  }

  @MyAnnotation(tr<caret>ue)
  void m() {

  }

}
