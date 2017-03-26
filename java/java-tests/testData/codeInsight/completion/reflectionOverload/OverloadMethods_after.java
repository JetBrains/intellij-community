class Main {
  void foo() {
    Test.class.getMethod("method", A.class, B.class);
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