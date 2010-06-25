// "Move assignment to field declaration" "true"

class X {
  static int f;
  static {
    f = <caret>0;
  }
  X(int i) {
  }
  void x() {
    int i = f;
  }
  void x2() {
    f = 9;
  }
}