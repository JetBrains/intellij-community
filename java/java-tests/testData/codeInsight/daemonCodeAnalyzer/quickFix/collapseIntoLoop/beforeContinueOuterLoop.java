// "Collapse into loop" "false"
class X {
  void test() {
    for (int i = 0; i < 10; i++) {
      <selection>if (i % 2 == 0) continue;
      if (i % 3 == 0) continue;
      if (i % 4 == 0) continue;
      if (i % 5 == 0) continue;</selection>
    }
  }
  
  void foo(Object obj) {}
}