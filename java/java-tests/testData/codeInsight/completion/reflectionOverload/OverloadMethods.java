class Main {
  void foo() {
    Test.class.getMethod("<caret>");
  }
}

class Test {
  public Test() {}
  Test(int n) {}
  public void method(){}
  void method(A a, B b){}
  public void method(C c){}
}

class A {}
class B {}
class C {}