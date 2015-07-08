class A {
  public void foo() {}
}

class B extends A {

  @Override
  public void foo() {
    super.foo();
  }
}