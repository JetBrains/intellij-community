// "Change field 'x' type to 'byte'" "true-preview"

record R(byte x) {
  R(byte x) {
    this.x = x;
  }
  
  void test() {
    byte b = x;
  }
}