class A {
  public void <caret>foo() {}
}
class Test extends A {
  @Override
  public void foo() {}
}