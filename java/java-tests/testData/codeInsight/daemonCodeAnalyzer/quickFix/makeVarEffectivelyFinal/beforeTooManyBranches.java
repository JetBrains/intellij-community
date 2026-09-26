// "Make 'x' effectively final by moving initializer to the 'if' statement" "false"
class X {
  void test(boolean a, boolean b, boolean c, boolean d) {
    int x = 0;
    if (a) {
      System.out.println("hello");
    } else if (b) {
      System.out.println("hello2");
    } else if (c) {
      System.out.println("hello3");
    } else if (d) {
      System.out.println("hello4");
    } else {
        x = 1;
    }
    Runnable r = () -> System.out.println(<caret>x);
  }
}