// "Make 'test()' return 'int'" "true-preview"

class Test {
  void test(int val) {
    return switch (val) {
      default -> 42<caret>;
    };
  }
}