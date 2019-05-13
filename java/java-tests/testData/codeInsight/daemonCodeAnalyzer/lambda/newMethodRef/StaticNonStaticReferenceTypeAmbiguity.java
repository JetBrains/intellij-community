class Test {

  interface I {
    void m(Test rec, String s);
  }

  void m(Test t, String s) {}
  void m(String s) {}

  static void m(Test t, Object s) {}

  static void test() {
    <error descr="Incompatible types. Found: '<method reference>', required: 'Test.I'">I i = Test::m;</error>
  }
}
