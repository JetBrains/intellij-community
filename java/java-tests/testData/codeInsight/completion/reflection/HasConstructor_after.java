class HasConstructor {
  void foo() {
    Test.class.getMethod("method1");
  }
}

class Test {
  public Test() {}
  Test(int n) {}
  public void method(){}
  public void method2(A a, B b){}
  void method1(){}
}

class A {}
class B {}
class C {}