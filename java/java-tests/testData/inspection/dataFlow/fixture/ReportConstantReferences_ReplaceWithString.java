class Test {
  public static final String CONST = "foo bar";
  private void test() {
    String s = CONST;
    System.out.println(<caret><warning descr="Value 's' is always 'CONST'">s</warning>);
  }

}