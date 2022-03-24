// "Remove unreachable branches" "true"
class Test {

  int test(Number n) {
      n = 1;
      Object i1 = n;
      System.out.println((int) i1 + 10);
      int i = 5;
      System.out.println(i);
  }
}