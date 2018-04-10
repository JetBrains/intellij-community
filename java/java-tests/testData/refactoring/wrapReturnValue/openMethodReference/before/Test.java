class Test {
  interface F {
    String get(Test t);
  }

  String foo() {
    return "";
  }
  
  {
    F f = Test::foo;
  }
}