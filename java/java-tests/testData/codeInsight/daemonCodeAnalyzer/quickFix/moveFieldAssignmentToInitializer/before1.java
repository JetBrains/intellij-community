// "Move assignment to field declaration" "true-preview"

class X {
  int f;
  X() {
    f = <caret>0;
  }
}