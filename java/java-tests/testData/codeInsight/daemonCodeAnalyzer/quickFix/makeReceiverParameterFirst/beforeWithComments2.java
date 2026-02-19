// "Move 'this' to the beginning of the list" "true-preview"
class X {
  void foo(X x, /*1*/ /*2*/ X /*3*/ this<caret> /*4*/ /*5*/, String s) {}
}