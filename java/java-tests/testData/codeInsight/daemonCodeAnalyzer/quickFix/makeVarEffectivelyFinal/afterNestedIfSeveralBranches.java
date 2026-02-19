// "Make 'x' effectively final by moving initializer to the 'if' statement" "true-preview"
class X {
  void test(boolean a, boolean b, boolean c) {
    int x;
    if (a) {
      if (b) {
        x = 1;
      } else {
          x = 0;
      }
    } else if (c) {
      x = 3;
    } else {
        x = 0;
    }
      Runnable r = () -> System.out.println(x);
  }
}