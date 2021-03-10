// "Move 'this' to the beginning of the list" "true"
class X {
  void foo(X x, double d, X this<caret>) {}
}