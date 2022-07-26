// "Create local variable 'x'" "true-preview"
class X {
  void m(String s) {}
  {
      String x;
      m(x);
    x.doSmth();
  }
}