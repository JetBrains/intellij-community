// "Collapse into loop" "false"
class X {
  void test() {
    for (int i = 0; i < 10; i++) {
      <selection>if (i % 2 == 0) break;
      if (i % 3 == 0) break;
      if (i % 4 == 0) break;
      if (i % 5 == 0) break;</selection>
    }
  }
  
  void foo(Object obj) {}
}