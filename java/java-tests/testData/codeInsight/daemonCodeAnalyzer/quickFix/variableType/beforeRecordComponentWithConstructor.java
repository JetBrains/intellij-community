// "Change field 'x' type to 'byte'" "true-preview"

record R(int x) {
  R(int x) {
    this.x = x;
  }
  
  void test() {
    byte b = <caret>x;
  }
}