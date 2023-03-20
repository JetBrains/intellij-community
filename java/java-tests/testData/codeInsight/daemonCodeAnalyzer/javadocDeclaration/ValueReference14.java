class Test {
  public static final int A = 1;

  /**
   * Value is {@value <error descr="@value tag may not have any arguments when JDK 1.4 or earlier is used">#A</error>}
   * @param ppp .
   */
  public void i(int ppp) {}
}