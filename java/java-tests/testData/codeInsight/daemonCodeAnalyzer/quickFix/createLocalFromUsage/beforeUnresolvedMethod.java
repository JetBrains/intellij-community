// "Create local variable 'x'" "true-preview"
class X {
  void m(String s) {}
  {
    m(x);
    <caret>x.doSmth();
  }
}