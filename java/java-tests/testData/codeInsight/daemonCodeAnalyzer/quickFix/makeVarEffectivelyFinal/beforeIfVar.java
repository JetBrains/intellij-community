// "Make 'x' effectively final by moving initializer to the 'if' statement" "true-preview"
class Abc {
  void test() {
    var x = 5;
    if (Math.random() > 0.5) {
      x = 10;
    }
    Runnable r = () -> System.out.println(<caret>x);
  }
}