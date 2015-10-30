interface A {
  void foo();
}

class B implements A {
  @Override
  public void f<caret>oo() {}
}