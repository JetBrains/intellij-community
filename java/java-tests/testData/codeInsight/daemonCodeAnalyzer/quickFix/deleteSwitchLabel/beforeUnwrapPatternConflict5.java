// "Remove unreachable branches" "true-preview"
class Test {
  void test(Number n) {
      n = 1;
      int result = switch (n) {
        case <caret>Integer i when i == 1 -> i;
        case Long l -> l.intValue();
        default -> 1;
      } + 10;
      int i = 5;
      System.out.println(result + i);
  }
}