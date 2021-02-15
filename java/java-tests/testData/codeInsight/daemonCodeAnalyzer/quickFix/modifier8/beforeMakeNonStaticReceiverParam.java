// "Make 'foo' not static" "true"
class X {
  static void foo(X this<caret>) {}
}