// "Make variable effectively final" "true-preview"
class X {
  void test(boolean a) {
    int x;
    if (a) {
      x = 1;
    } else {
        x = 0;
    }
      Runnable r = () -> System.out.println(x);
  }
}