class Main {
  void foo() {
    Class<?> d = d();
    d = d();
    int i = 0;
    (d).getDeclaredMethod("<caret>");
  }
  Class<Test> d() {
    return Test.class;
  }
}

class Test {
  public int method() {}
  int method2(int n) {}
}