class Test {
  void test(int x) {
    if (x < 0 || x > 2) return;
    int result = switch(x) {
      case 0 -> 1;
      case 1 -> 2;
      case 2 -> 3;
      default -> 0; // unreachable but required by compiler
    };
  }
}