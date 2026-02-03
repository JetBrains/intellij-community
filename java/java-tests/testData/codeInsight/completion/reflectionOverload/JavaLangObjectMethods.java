class Main {
  void foo() {
    Test.class.getMethod("<caret>");
  }
}

class Test {
  public void method(){}
}