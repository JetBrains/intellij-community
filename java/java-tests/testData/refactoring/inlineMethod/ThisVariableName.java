class Of {
  private int i = 0;

  public static void main(String[] args) {
    Of inlineMethods = new Of();
    inlineMethods.test();
    inlineMethods.test2();
  }

  private void test() {
    this.test2();
  }

  private void te<caret>st2() {
    int i1 = this.i;
    this.i = 2;
    System.out.println(i1);
    this.test();
  }
}