// "Collapse into loop" "true"
class X {
  void test() {
    LOOP:
    for (int i = 0; i < 10; i++) {
      <selection>if (i % 2 == 0) break LOOP;
      if (i % 3 == 0) break LOOP;
      if (i % 4 == 0) break LOOP;
      if (i % 5 == 0) break LOOP;</selection>
    }
  }
  
  void foo(Object obj) {}
}