class Test {
  private static final int CONST = 3/2 + 0*1;
  public void foo() {
    int i = 0;
    int j = 2 + (CONST) - 6/2;
    if (<warning descr="Condition 'i == j' is always 'true'">i == j</warning>) {
    }
  }
}