// "Create local variable 'x'" "true"
class X {
  void m(String s) {}
  {
    m(x);
    <caret>x.doSmth();
  }
}