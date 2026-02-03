// "Remove unreachable branches" "true-preview"
class X {
  int test(int a, int b) {
    if (b != 0) return;
    return switch(a) {
      case 1 -> 2;
      default -> 6;
    }
  }
}