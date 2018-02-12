class Test {
  public int A = 1;

  /**
   * Value is {@value <error descr="@value tag must reference a static field">#A</error>}
   */
  public void i() {}
}