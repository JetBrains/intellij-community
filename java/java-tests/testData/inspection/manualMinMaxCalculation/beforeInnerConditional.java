// "Replace with 'Math.max()' call" "true"
class Test {

  void test(int a, int b, int c) {
    int d = a > b ? <caret>a > c ? a : c : b;
  }
}