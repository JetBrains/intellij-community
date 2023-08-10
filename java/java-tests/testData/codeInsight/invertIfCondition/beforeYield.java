// "Invert 'if' condition" "true-preview"
class A {
  int test(int x, int y) {
    return switch (y) {
      default -> {
        if (<caret>x > 10) {
          yield 11;
        }
        yield 21;
      }
    };
  }
}