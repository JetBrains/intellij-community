// "Change field 'x' type to 'byte'" "true-preview"

record R(byte x) {
  void test() {
  byte b = x;
  }
}