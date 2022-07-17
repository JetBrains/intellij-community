// "Remove unreachable branches" "true"
class Test {
  void test(Number n) {
      n = 1;
      int result = switch (n) {
        case <caret>Integer i && i == 1 -> i;
        case Long l -> l.intValue();
        case default -> 1;
      } + 10;
      int i = 5;
      System.out.println(result + i);
  }
}