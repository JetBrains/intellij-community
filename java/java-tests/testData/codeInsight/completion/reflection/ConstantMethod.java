class Main {
  final Class<?> clazz = Test.class;
  void foo() {
    clazz.getMethod("<caret>");
  }
}

class Test {
  public void method(){}
  void method2(int n){}
}