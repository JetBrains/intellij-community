class Test {

  interface I1 {String m();}
  interface I2 {void m();}

  void call(I1 p) { }
  void call(I2 p) { }
 
  void test() {
    call(() -> { throw new RuntimeException(); });
    call(() -> { if (true) return ""; throw new RuntimeException(); });
    call(() -> { if (true) return; throw new RuntimeException(); });
  }
}
