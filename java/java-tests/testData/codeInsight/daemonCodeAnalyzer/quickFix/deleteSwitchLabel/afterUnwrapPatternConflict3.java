// "Remove unreachable branches" "true-preview"
class Test {
  void test(Number n) {
      n = 1;
      Integer i1 = (Integer) n;
      System.out.println(i1);
      Object i = "";
  }
}