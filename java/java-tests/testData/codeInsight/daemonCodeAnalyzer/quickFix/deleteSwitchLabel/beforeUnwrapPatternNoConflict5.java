// "Remove unreachable branches" "true-preview"
class Test {

  int test(Number n) {
      n = 1;
      return 1 + switch (n) {
        case <caret>Integer i when i == 1 -> i;
        case Long l -> l.intValue();
        default -> 1;
      };
  }
}