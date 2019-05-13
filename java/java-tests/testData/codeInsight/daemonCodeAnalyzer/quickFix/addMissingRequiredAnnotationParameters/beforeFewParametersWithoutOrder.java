// "Add missing annotation parameters - value4, value1" "true"
class Test {

  @MyAnnotati<caret>on(value2 = "", value3 = "")
  void m() {

  }

  @interface MyAnnotation {
    String value4();
    String value3();
    String value2();
    String value1();
  }

}