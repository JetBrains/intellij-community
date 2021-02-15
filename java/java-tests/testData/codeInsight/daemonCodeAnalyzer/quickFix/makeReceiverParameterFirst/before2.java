// "Move 'this' to the begin of the list" "true"
class X {
  void foo(X x, double d, X this<caret>) {}
}