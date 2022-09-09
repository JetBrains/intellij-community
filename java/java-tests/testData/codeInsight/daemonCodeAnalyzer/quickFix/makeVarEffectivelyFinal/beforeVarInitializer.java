// "Make variable effectively final" "true-preview"
class X {
  void test(boolean a, int y) {
    int x = y;
    if (a) {
      x = 1;
    }
    Runnable r = () -> System.out.println(<caret>x);
  }
}