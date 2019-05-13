class Test {

  @MyAnnotation(value3 = "value33", value2 = "value22", value1 = "value11")
  void m() {

  }

  @interface MyAnnotation {
    String value3();
    String value2();
    String value1();
  }

}