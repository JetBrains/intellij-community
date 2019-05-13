// "Add missing annotation parameter 'value'" "true"
class Test {

  @MyAnnotation()
  void m() {

  }

  @interface MyAnnotation {
    String value();
  }

}