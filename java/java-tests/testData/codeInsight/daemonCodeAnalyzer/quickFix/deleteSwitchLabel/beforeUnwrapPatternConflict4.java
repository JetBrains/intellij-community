// "Remove unreachable branches" "true"
class Test {
  void test(Number n) {
      n = 1;
      switch (n) {
        case <caret>Integer i && i == 1:
          System.out.println(i);
          break;
        case Long s:
          System.out.println(s);
          break;
        default:
          System.out.println();
          break;
      }
      {
        Object i = "";
      }
  }
}