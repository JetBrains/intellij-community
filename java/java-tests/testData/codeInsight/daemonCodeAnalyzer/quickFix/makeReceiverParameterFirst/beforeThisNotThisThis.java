// "Move 'this' to the beginning of the list" "false"
class X {
  void foo(X this, int i, X this<caret>) {}
}