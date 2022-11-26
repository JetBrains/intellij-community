// "Remove unreachable branches" "true-preview"
class X {
  int test(int a, int b) {
    if (b != 0) return;
    return switch(a) {
      case 1 -> switch(b) {
        case <caret>0 -> 2;
        case 1 -> 3;
        case 2 -> 4;
        default -> 5;
      }
      default -> 6;
    }
  }
}