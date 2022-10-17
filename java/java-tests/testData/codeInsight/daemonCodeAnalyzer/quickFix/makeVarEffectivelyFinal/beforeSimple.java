// "Make 'x' effectively final by moving initializer to the 'if' statement" "true-preview"
class X {
  void test(boolean a) {
    int x = 0;
    if (a) {
      x = 1;
    }
    Runnable r = () -> System.out.println(<caret>x);
  }
}