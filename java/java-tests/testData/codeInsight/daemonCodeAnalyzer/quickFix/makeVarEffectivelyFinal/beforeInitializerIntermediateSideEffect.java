// "Make 'x' effectively final by moving initializer to the 'if' statement" "false"
class X {
  void test(boolean a, int b, int c) {
    int x = b * c;
    b++;
    if (a) {
      x = 1;
    }
    Runnable r = () -> System.out.println(<caret>x);
  }
}