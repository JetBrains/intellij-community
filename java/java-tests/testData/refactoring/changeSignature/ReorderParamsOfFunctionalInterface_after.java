interface I {
  void m(int b, boolean a);
}

class Test {
  {
    I i = (b, a) -> {};
  }
}