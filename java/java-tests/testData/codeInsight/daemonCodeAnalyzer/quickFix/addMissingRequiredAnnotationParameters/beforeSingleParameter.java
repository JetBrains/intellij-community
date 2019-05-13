// "Add missing annotation parameter 'value'" "true"
class Test {

  @MyAnnotati<caret>on
  void m() {

  }

  @interface MyAnnotation {
    String value();
  }

}