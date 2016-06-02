interface Test {
  /**
   * some javadoc
   */
  default void foo() {
    System.out.println("I");
  }
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