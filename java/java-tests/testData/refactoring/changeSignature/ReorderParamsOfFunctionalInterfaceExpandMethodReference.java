interface I {
  void m<caret>(int a, int b);
}

class Test {
  {
    I i = this::foo;
  }
  
  private void foo(int a, int b) {}
}