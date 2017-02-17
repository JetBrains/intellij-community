class Main {
  void foo() {
    Test.class.getMethod("pMethod", C.class);
  }
}

class Test extends Parent {
  public void method(){}
  public void pMethod(C c){}
  public void gpMethod(A a, B b){}
}

class Parent extends GrandParent {
  public void pMethod(C c){}
}

class GrandParent {
  public void gpMethod(A a, B b){}
}

class A {}
class B {}
class C {}