// "Move assignment to field declaration" "true-preview"

class X {
  String e, ff;

  void f() {
    ff <caret>= //comment
      "";
  }
}