// "Remove receiver parameter" "true-preview"
class X {
  void foo(X x, X this<caret>) {}
}