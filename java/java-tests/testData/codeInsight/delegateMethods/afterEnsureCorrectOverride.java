interface I {
  void foo();
}
class A implements I {
  @Override
  public void foo() {
    
  }
}

class B {
  A a;

    public void foo() {
        a.foo();
    }
}