// "Make variable effectively final" "true-preview"
class X {
  void test(boolean a, boolean b, boolean c) {
    int x = 0;
    if (a) {
      if (b) {
        x = 1;
      } else {
        x = 2;
      }
    } else if (c) {
      x = 3;
    }
    Runnable r = () -> System.out.println(<caret>x);
  }
}