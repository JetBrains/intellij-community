// "Move initializer to constructor" "true-preview"
class X {
  final String s;

  X() {
      s = {};
  }
}