// "Add missing annotation parameters - value3, value2, value1" "true-preview"
class Test {

  @MyAnnotati<caret>on
  void m() {

  }

  @interface MyAnnotation {
    String value3();
    String value2();
    String value1();
  }

}