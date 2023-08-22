// "Make 'x' effectively final by moving initializer to the 'if' statement" "true-preview"
class X {
  void test(boolean a, int b, int c) {
    int x = b * c;
    int y = b / c;
    if (a) {
      x = 1;
    }
    Runnable r = () -> System.out.println(<caret>x);
  }
}