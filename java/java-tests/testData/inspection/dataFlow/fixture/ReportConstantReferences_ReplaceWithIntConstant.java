class Test {
  public static final int CONST = 23942;
  private void test(int a) {
    if (a == CONST) {
      System.out.println(<caret><warning descr="Value 'a' is always 'CONST'">a</warning>);
    }

  }

}