class A {
  public void <caret>foo(int i) {}
}
class Test extends A {
  @Override
  public void foo(int i) {}
}