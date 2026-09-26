public abstract class A {
  @SuppressWarnings("UnusedDeclaration")
  public abstract void foo();
  public void bar() {
    foo();
  }
  public void baz() {
    bar();
  }
}

