// "Change field 'x' type to 'byte'" "true"

record R(byte x) {
  void test() {
  byte b = x;
  }
}