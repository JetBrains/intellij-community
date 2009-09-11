class Test {
  String foo() {
    return new Wrapper("").getMyField();
  }

  void bar() {
    String s = foo();
  }

}