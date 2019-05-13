// "Remove redundant assignment" "true"
class A {
  static int n;
  static {
  }
  static { n = 2; }
}