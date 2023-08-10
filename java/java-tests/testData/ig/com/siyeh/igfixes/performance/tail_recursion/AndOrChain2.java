class Test {
  boolean test(int x, int y) {
    return x < 10 && (y > 10 || <caret>test(x - 1, y % 2));
  }
}