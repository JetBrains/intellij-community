class Main {
  void foo() {
    Class<Test> c = bar();
    c.getMethod("<caret>");
  }

  Class bar() { return Test.class; }
}

class Test {
  public void method(){}
  public void method2(int n){}
}
