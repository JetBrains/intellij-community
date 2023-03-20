// "Add missing annotation parameters - value4, value1" "true-preview"
class Test {

  @MyAnnotati<caret>on(value3 = "", value2 = "")
  void m() {

  }

  @interface MyAnnotation {
    String[] value4();
    String value3();
    String value2();
    String[] value1();
  }

}