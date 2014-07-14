// "Add missing annotation parameters - value4, value1" "true"
class Test {

  @MyAnnotation(value2 = "", value3 = "", value4 = , value1 = )
  void m() {

  }

  @interface MyAnnotation {
    String value4();
    String value3();
    String value2();
    String value1();
  }

}