// "Remove redundant assignment" "true-preview"
class X {
  void test(boolean b) {
    if (<caret>b ^= false) {}
  }
}