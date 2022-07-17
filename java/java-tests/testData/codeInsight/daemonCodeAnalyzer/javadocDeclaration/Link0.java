class Test {
  /**
   * @param ppp see {@link #<error descr="Cannot resolve symbol 'Test'">Test</error>}
   */
  public void i(int ppp) {}

  class A {
    public void foo() {}
  }
}