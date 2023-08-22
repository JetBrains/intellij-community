// "Move 'this' to the beginning of the list" "false"
class X {
  void foo(X this, X this<caret>) {}
}