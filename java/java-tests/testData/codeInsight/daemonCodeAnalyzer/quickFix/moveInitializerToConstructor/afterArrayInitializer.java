// "Move initializer to constructor" "true"
class X {
  final String s;

  X() {
      s = {};
  }
}