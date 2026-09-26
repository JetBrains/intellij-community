// "Replace method call with 'Test2.mmm()'" "false"
class Test {
  static void example() {
    Test t = new Test();
    t.m<caret>mm();
  }

  /**
   * {@link Test2#mmm()}
   *
   */
  @Deprecated
  void mmm() {
  }

}

class Test2 {
  void mmm() {
  }
}
