class Test {
  void test(int <flown1>x) {
    System.out.println(<caret>x);
  }

  void use(int[] <flown111>x, int idx) {
    test(<flown11>x[idx]);
  }

  void use2(int idx) {
    use(<flown1111>new int[] {-1, <flown11111>0, <flown11112>1}, idx);
  }
}