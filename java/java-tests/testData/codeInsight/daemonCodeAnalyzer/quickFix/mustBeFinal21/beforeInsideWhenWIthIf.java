// "Make 'mode' effectively final by moving initializer to the 'if' statement" "true-preview"
class Test {
  void test(Object o) {
    int mode = 2;
    if (Math.random() > 0.5) {
      mode = 3;
    }
    switch (o) {
      case Integer i when i == mode<caret> -> {}
      default -> {}
    }
  }
}