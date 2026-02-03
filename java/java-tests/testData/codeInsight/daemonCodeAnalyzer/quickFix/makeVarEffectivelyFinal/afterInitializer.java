// "Make 'x' effectively final by moving initializer to the 'if' statement" "true-preview"
class X {
  void test(boolean a, int b, int c) {
    int x;
    int y = b / c;
    if (a) {
      x = 1;
    } else {
        x = b * c;
    }
      Runnable r = () -> System.out.println(x);
  }
}