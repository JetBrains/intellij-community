// "Replace with 'Math.min'" "true"
class Test {
  void test(int a, int b) {
    int c = a<caret> <= b ? a : b;
  }
}