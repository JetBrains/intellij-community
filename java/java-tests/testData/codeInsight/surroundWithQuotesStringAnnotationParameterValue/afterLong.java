// "Surround annotation parameter value with quotes" "true"
class X {

  @interface MyAnnotation {
    int value0();
    String value();
    long value1();
  }

  @MyAnnotation(value0 = 1, value= "1000L", value1 = 10L)
  void m() {

  }

}
