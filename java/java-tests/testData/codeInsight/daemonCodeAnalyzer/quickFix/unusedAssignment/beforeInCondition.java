// "Remove redundant assignment" "true"
class X {
  void test(boolean b) {
    if (<caret>b ^= false) {}
  }
}