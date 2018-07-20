// "Move assignment to field declaration" "true"

class X {
  String e, ff;

  void f() {
    ff <caret>= //comment
      "";
  }
}