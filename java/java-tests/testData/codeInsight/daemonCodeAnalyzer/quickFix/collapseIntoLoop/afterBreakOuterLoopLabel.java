// "Collapse into loop" "true"
class X {
  void test() {
    LOOP:
    for (int i = 0; i < 10; i++) {
        for (int j = 2; j < 6; j++) {
            if (i % j == 0) break LOOP;
        }
    }
  }
  
  void foo(Object obj) {}
}