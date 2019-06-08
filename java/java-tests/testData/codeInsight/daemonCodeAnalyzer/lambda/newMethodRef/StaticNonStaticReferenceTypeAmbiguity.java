class Test {

  interface I {
    void m(Test rec, String s);
  }

  void m(Test t, String s) {}
  void m(String s) {}

  static void m(Test t, Object s) {}

  static void test() {
    I i = Test::<error descr="Cannot resolve method 'm'">m</error>;
  }
}
