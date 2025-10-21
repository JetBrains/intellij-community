// "Make 'x' effectively final by moving initializer to the 'if' statement" "true-preview"
class Abc {
  void test() {
      int x;
    if (Math.random() > 0.5) {
      x = 10;
    } else {
        x = 5;
    }
      Runnable r = () -> System.out.println(x);
  }
}