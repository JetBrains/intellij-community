interface I {
  void m<caret>(int a, int b);
}

class Test {
  {
    I i = (a, b) -> {};
  }
}