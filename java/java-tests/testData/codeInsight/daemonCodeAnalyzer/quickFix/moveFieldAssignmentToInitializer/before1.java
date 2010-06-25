// "Move assignment to field declaration" "true"

class X {
  int f;
  X() {
    f = <caret>0;
  }
}