// "Remove redundant assignment" "true-preview"
class A {
  static int n;
  static { <caret>n = 1; }
  static { n = 2; }
}