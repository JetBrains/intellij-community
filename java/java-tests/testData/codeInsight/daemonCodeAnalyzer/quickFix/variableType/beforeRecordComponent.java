// "Change field 'x' type to 'byte'" "true-preview"

record R(int x) {
  void test() {
  byte b = <caret>x;
  }
}