class Main {
  void foo() {
    Test.class.getMethod("method2");
  }
}

class Test extends Parent {
  public void method(){}
  void method3(){}
}

class Parent {
  public void method2(){}
  void method4(){}
}