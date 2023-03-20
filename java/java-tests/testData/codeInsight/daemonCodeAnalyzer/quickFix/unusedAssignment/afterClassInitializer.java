// "Remove redundant assignment" "true-preview"
class A {
  static int n;
  static {
  }
  static { n = 2; }
}