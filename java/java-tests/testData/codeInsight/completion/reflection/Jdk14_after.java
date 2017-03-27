class Main {
  void foo() {
    Test.class.getMethod("method");
  }
}

class Test {
  public void method(){}
}