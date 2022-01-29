// "Replace with 'Math.min()' call" "true"
class X {
  void test(int a, int b) {
    int c = Math.min(a, b);
      System.out.println(c);
  }
}