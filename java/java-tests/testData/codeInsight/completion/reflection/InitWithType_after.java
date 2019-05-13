class Main {
  void foo() {
    Class<Test> c = bar();
    c.getMethod("method2", int.class);
  }

  Class bar() { return Test.class; }
}

class Test {
  public void method(){}
  public void method2(int n){}
}
