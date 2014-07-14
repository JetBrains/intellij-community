class Test {

  interface I1 {String m();}
  interface I2 {void m();}

  void call(I1 p) { }
  void call(I2 p) { }
 
  void test() {
    call<error descr="Ambiguous method call: both 'Test.call(I1)' and 'Test.call(I2)' match">(() -> { throw new RuntimeException(); })</error>;
    call(() -> { if (true) return ""; throw new RuntimeException(); });
    call(() -> { if (true) return; throw new RuntimeException(); });
  }
}
