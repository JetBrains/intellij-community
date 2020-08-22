// "Change field 'x' type to 'byte'" "true"

record R(int x) {
  void test() {
  byte b = <caret>x;
  }
}