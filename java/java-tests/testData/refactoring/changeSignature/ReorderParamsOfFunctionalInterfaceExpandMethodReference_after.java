interface I {
  void m(int b, int a);
}

class Test {
  {
    I i = (b, a) -> foo(a, b);
  }
  
  private void foo(int a, int b) {}
}