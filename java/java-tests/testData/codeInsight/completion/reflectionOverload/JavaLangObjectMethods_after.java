class Main {
  void foo() {
    Test.class.getMethod("notifyAll");
  }
}

class Test {
  public void method(){}
}