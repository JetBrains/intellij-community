class Test {
  private static final int <caret>FIELD = 5;

  /**
   * Prints the value of {@link Test#FIELD}
   */
  void test() {
    System.out.println(FIELD);
  }
}