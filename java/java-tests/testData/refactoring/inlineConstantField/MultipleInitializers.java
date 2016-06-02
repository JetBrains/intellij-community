class Test {
  private final int <caret>i;

  public Test() {
    this.i = 0;
  }

  public Test(String s) {
    this.i = s.length();
  }

  void foo() {
    System.out.println(i);
  }
}