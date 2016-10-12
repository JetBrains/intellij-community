interface Test {
  /**
   * some javadoc
   */
  void foo();
}

class C implements Test {
  /**
   * another javadoc
   */
  @Override
  public void foo() {
    System.out.println("C");
  }
}