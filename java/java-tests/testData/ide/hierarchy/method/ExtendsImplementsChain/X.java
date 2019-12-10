interface A {
  public void foo();
}
class B implements A {
  @Override
  public void foo() {}
}

class C extends B {
  @Override
  public void foo() {
    System.out.println("C.foo");
  }
}