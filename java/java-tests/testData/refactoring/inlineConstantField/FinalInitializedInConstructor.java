class Test {
  private final int <caret>i;

  public Test() {
    this.i = 0;
  }

  public Test(String s) {
    this.i = 0;
  }

  void foo() {
    System.out.println(i);
  }
}