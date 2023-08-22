// "Make 'x' effectively final by moving initializer to the 'if' statement" "true-preview"
class X {
  void test(boolean a, boolean b, boolean c) {
    int x = 0;
    if (a) {
      System.out.println("hello");
      x = 1;
    } else if (b) {
      System.out.println("hello2");
    } else if (c) {
      System.out.println("hello3");
    }
    Runnable r = () -> System.out.println(<caret>x);
  }
}