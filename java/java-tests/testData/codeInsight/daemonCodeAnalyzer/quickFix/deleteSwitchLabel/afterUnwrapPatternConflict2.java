// "Remove unreachable branches" "true-preview"
class Test {
  void test(Number n) {
      n = 1;
      {
          Integer i = (Integer) n;
          int j = 1;
          System.out.println(i);
      }
      Object j = "";
  }
}