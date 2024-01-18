class Test {
  int test(int x) {
    if (x < 0) {
      return 2;
    }
    if (x > 0) {
      return 1;
    }
    if (x == 0) {
      return 3;
    }
    return 0;
  }
}