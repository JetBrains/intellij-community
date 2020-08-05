// "Add 'int' as 1st record component to record 'R'" "true"
record R(int x) {}

class X {
  void test() {
    new R(1, <caret>2);
  }
}