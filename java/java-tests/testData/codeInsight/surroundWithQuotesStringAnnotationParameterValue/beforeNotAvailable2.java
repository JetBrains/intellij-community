// "Surround annotation parameter value with quotes" "false"
class X {

  @interface MyAnnotation {
    int value();
  }

  @MyAnnotation(tr<caret>ue)
  void m() {

  }

}
