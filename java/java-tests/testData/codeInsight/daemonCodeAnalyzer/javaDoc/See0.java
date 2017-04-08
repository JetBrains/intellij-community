class Test {
  /**
   * @see A#<error descr="Cannot resolve symbol 'someField'">someField</error>
   */
  public void i() {}

  class A {
    public void foo() {}
  }
}