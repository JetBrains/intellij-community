// "Move initializer to constructor" "true"
class X {
  final String <caret>s = {};

  X() {
  }
}