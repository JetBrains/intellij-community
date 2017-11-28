class Main {
  void foo() {
    Test.class.getDeclaredMethod("method3");
  }
}

class Test extends Parent {
  public void method(){}
  void method3(){}
  private void method5(){}
}

class Parent {
  public void method2(){}
  void method4(){}
  private void method6(){}
}