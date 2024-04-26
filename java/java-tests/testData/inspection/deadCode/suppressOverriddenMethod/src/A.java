public abstract class A {
  @SuppressWarnings("UnusedDeclaration")
  public abstract void foo();

  @SuppressWarnings("UnusedDeclaration")
  public void bar() {
    foo();
    new B;
  }
}

