// "Invert 'if' condition" "true-preview"
class A {
  int test(int x, int y) {
    return switch (y) {
      default -> {
          if (x <= 10) {
              yield 21;
          }
          else {
              yield 11;
          }
      }
    };
  }
}