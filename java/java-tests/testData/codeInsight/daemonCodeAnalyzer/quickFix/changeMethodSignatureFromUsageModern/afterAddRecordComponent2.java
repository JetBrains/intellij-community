// "Add 'int' as 1st record component to record 'R'" "true"
record R(int i, int x) {
  R(int i, int x) {
    this.x = x;
  }
}

class X {
  void test() {
    new R(1, 2);
  }
}