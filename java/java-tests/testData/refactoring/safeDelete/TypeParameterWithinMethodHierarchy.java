class A {
  public <<caret>T> void foo() {}
}

class B extends A {

  @Override
  public <T> void foo() {
    super.foo();
  }
}