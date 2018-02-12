class Test {
  public static int A;

  /**
   * Value is {@value <error descr="@value tag must reference a field with a constant initializer">#A</error>}
   */
  public void i() {}
}