// "Add missing annotation parameter 'value'" "true-preview"
class Test {

  @MyAnnotati<caret>on
  void m() {

  }

  @interface MyAnnotation {
    String value();
  }

}