class Test {
  /**
   * Test. Don't report type param 'X' as duplicate of param 'X'.
   *
   * @param X my string
   * @param <X> my type
   */
  <X> X getValue(String X) {return null;}
}
