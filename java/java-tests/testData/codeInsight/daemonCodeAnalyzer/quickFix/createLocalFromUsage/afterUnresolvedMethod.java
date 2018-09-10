// "Create local variable 'x'" "true"
class X {
  void m(String s) {}
  {
      String x;
      m(x);
    x.doSmth();
  }
}