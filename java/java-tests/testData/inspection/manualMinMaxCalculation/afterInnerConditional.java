// "Replace with 'Math.max'" "true"
class Test {

  void test(int a, int b, int c) {
    int d = a > b ? Math.max(a, c) : b;
  }
}