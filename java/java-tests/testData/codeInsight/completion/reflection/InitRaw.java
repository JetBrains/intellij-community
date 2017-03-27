class Main {
  void foo() {
    Class<?> c = Test.class;
    c.getMethod("<caret>");
  }
}

class Test {
  public void method(){}
  public void method2(int n){}
}
