// "Replace method call with Test.mmm1" "true"
class Test {
  static void example() {
    Test t = new Test();
    Test.mmm1("");
  }

  /**
   * {@link Test#mmm1(String)}
   *
   */
  @Deprecated
  void mmm(String t) {
  }

  static void mmm1(String string) {

  }
}