class Test {

  interface I0 {
    void m();
  }

  interface I1 {
    void m(Object a);
  }

  interface I2 {
    void m(Object a1, Object a2);
  }

  interface IVarargs {
    void m(Object... as);
  }

  void call(I0 p) { }
  void call(I1 p) { }
  void call(I2 p) { }
  void call(IVarargs p) { }

  void test() {
    call(() -> { });
    <error descr="Ambiguous method call: both 'Test.call(I1)' and 'Test.call(IVarargs)' match">call</error>(p1 -> { });
    call((p1, p2) -> {});
  }
}
