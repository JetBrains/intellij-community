// "Remove unreachable branches" "true"
class Test {

  int test(Number n) {
      n = 1;
      Integer i = (Integer) n;
      return 1 + i;
  }
}