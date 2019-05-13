// "Move assignment to field declaration" "GENERIC_ERROR_OR_WARNING"

class X {
  static int f;
  static {
    /*
    Set to zero
     */
    <caret>f = 0;
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