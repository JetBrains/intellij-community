class Test {
  public Test() {
    this(Const.CONST);
  }

  public Test(int l) {}

  private class Const {
    private static final int CONST = 42;
  }
}