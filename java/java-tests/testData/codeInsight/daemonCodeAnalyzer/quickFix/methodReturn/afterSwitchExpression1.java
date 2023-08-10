// "Make 'test()' return 'int'" "true-preview"

class Test {
  int test(int val) {
    return switch (val) {
      default -> 42;
    };
  }
}