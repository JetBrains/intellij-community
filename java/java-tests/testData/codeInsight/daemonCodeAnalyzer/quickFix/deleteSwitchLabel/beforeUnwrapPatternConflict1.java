// "Remove unreachable branches" "true"
class Test {
  void test(Number n) {
      n = 1;
      switch (n) {
        case <caret>Integer i && i == 1 -> {
          int j = 1;
          System.out.println(i);
        }
        case Long s -> System.out.println(s);
        default -> System.out.println();
      }
      {
        Object j = "";
      }
  }
}