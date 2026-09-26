// "Move assignment to field declaration" "true-preview"

class X {
  String ff;

  void f() {
    ff <caret>= ""; //comment
  }
}