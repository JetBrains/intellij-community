// "Make 'foo()' not static" "true-preview"
class X {
  static void foo(X this<caret>) {}
}