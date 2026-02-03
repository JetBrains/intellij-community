class X {
  void m() {}
  public void f() {}
}
class Y extends X {
  @Override
  public void f<caret>() {}

  @Override
  void m() {
    super.m();
  }
}