// "Add missing annotation parameters - value4, value3, value2, value1" "true"
class Test {

  @MyAnnotati<caret>on("xxx")
  void m() {

  }

  @interface MyAnnotation {
    String value();
    String value4();
    String value3();
    String value2();
    String value1();
  }

}