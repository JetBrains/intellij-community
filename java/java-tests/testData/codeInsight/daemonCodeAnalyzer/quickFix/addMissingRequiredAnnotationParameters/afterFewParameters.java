// "Add missing annotation parameters - value3, value2, value1" "true"
class Test {

  @MyAnnotation(value3 = , value2 = , value1 = )
  void m() {

  }

  @interface MyAnnotation {
    String value3();
    String value2();
    String value1();
  }

}