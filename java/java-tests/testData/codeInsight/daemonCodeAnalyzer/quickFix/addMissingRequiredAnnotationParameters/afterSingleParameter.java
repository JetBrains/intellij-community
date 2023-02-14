// "Add missing annotation parameter 'value'" "true-preview"
class Test {

  @MyAnnotation("")
  void m() {

  }

  @interface MyAnnotation {
    String value();
  }

}