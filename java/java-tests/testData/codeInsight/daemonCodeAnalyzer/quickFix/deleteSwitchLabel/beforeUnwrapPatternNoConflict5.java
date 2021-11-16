// "Remove unreachable branches" "true"
class Test {

  int test(Number n) {
      n = 1;
      return 1 + switch (n) {
        case <caret>Integer i && i == 1 -> i;
        case Long l -> l.intValue();
        case default -> 1;
      };
  }
}