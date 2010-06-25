// "Move assignment to field declaration" "true"

class X {
  static int f = 0;
  static {
  <caret>}
  X(int i) {
  }
  void x() {
    int i = f;
  }
  void x2() {
    f = 9;
  }
}