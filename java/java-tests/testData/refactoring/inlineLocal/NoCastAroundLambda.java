class Test {
  void test() {
    I b1 = System::exit;
    a(b<caret>1);
  }

  void a(I b) {}

  interface I {
    void i(int i);
  }
}
