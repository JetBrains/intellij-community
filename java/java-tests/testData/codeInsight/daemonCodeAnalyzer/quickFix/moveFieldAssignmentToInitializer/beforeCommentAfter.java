// "Move assignment to field declaration" "true"

class X {
  String ff;

  void f() {
    ff <caret>= ""; //comment
  }
}