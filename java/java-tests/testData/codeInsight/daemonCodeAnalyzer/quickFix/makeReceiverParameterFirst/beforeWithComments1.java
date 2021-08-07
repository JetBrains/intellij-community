// "Move 'this' to the beginning of the list" "true"
class X {
  void foo(X x, /*1*/ /*2*/ X /*3*/ this<caret> /*4*/ /*5*/) {}
}