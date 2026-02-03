// "Replace with '=='" "true"
class Test {
  void test(int x) {
    if (x >= 10) {
      if (x <<caret>= 10) {}
    }
  }
}