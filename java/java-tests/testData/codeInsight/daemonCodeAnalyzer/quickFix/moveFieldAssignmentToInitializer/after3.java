// "Move assignment to field declaration" "GENERIC_ERROR_OR_WARNING"

class X {
  /*
    Set to zero
     */
    static int f = 0;

    X(int i) {
  }
  void x() {
    int i = f;
  }
  void x2() {
    f = 9;
  }
}