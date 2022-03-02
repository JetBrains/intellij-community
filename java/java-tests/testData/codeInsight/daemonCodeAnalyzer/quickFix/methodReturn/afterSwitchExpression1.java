// "Make 'test' return 'int'" "true"

class Test {
  int test(int val) {
    return switch (val) {
      default -> 42;
    };
  }
}