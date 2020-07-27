class A {
  public void f<caret>oo() {}
}

class B extends A {
  @Override
  public void foo() {}

  void err() {
    super.foo();
  }
}

class C extends B {
  @Override
  public void foo() {}
}

class B1 extends A {
  @Override
  public void foo() {}

  void err() {
    super.foo();
  }
}

interface IA {
  void foo();
}

class C1 extends B implements IA {
  @Override
  public void foo() { }
}