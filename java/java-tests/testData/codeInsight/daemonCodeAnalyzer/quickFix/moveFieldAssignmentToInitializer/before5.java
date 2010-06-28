// "Move assignment to field declaration" "true"

class X {
  int f;
  X() {
  }
  void x() {
    int i = f;
  }
  void x2() {
    <caret>f = 9;
  }
}