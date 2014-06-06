@Deprecated
class Test {
  protected Test() {

  }

  public void foo(){}
}

class D {
  static void foo(Test t) {
    t.foo();
  }
}