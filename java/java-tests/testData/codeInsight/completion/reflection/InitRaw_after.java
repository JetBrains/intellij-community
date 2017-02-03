class Main {
  void foo() {
    Class<?> c = Test.class;
    c.getMethod("method2", int.class);
  }
}

class Test {
  public void method(){}
  public void method2(int n){}
}
