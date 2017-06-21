// "Remove redundant assignment" "true"
class A {
  static int n;
  static { <caret>n = 1; }
  static { n = 2; }
}