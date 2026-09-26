// "Make 'x' effectively final by moving initializer to the 'if' statement" "true-preview"
class X {
  void test(boolean a, boolean b, boolean c) {
    int x;
    if (a) {
        x = 0;
        System.out.println("hello");
    } else if (b) {
        x = 0;
        System.out.println("hello2");
    } else if (c) {
        x = 0;
        System.out.println("hello3");
    } else {
      x = 1;
    }
    Runnable r = () -> System.out.println(x);
  }
}