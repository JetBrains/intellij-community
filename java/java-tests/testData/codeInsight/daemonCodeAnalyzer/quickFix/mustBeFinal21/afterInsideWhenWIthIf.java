// "Make 'mode' effectively final by moving initializer to the 'if' statement" "true-preview"
class Test {
  void test(Object o) {
    int mode;
    if (Math.random() > 0.5) {
      mode = 3;
    } else {
        mode = 2;
    }
      switch (o) {
      case Integer i when i == mode -> {}
      default -> {}
    }
  }
}