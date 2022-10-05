// "Make variable effectively final" "false"
class X {
  void test(boolean a, int i) {
    int x = i++;
    if (a > i) {
      x = 1;
    }
    Runnable r = () -> System.out.println(<caret>x);
  }
}