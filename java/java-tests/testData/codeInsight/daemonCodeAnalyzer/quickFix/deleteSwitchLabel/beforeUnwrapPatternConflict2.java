// "Remove unreachable branches" "true-preview"
class Test {
  void test(Number n) {
      n = 1;
      switch (n) {
        case <caret>Integer i when i == 1:
          int j = 1;
          System.out.println(i);
          break;
        case Long s:
          System.out.println(s);
          break;
        default:
          System.out.println();
          break;
      }
      Object j = "";
  }
}