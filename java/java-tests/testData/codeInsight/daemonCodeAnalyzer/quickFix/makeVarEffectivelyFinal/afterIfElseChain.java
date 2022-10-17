// "Make 'x' effectively final by moving initializer to the 'if' statement" "true-preview"
class X {
  void test(boolean a, boolean b, boolean c) {
    int x;
    if (a) {
      System.out.println("hello");
      x = 1;
    } else {
        x = 0;
        if (b) {
          System.out.println("hello2");
        } else if (c) {
          System.out.println("hello3");
        }
    }
    Runnable r = () -> System.out.println(x);
  }
}