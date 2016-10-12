class A {
  public void foo(int i) {}
}
class Test extends A {
  @Override
  public void <caret>foo(int i) {}
}