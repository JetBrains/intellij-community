// "Move initializer to constructor" "true-preview"
class X {
  final String <caret>s = {};

  X() {
  }
}