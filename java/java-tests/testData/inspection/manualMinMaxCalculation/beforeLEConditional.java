// "Replace with 'Math.min()' call" "true"
class Test {
  void test(int a, int b) {
    int c = a<caret> <= b ? a : b;
  }
}