// "Remove unreachable branches" "true"
class Test {
  void test(Number n) {
      n = 1;
      Integer i1 = (Integer) n;
      int result = i1 + 10;
      int i = 5;
      System.out.println(result + i);
  }
}