// "Make 'x' effectively final by moving initializer to the 'if' statement" "true-preview"
class X {
  void test(boolean a, boolean b, boolean c) {
    int x = 0;
    if (a) {
      System.out.println("hello");
    } else if (b) {
      System.out.println("hello2");
    } else if (c) {
      System.out.println("hello3");
    } else {
      x = 1;
    }
    Runnable r = () -> System.out.println(<caret>x);
  }
}