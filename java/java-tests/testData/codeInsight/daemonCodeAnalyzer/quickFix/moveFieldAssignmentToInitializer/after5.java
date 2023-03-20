// "Move assignment to field declaration" "true-preview"

class X {
  int f = 9;
  X() {
  }
  void x() {
    int i = f;
  }
  void x2() {
  <caret>}
}