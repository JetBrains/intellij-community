// "Replace method call with Test.mmm2" "true"
class Test {
  static void example() {
    Test t = new Test();
    t.m<caret>mm("");
  }

  /**
   * @deprecated use {@link #mmm1(int)} or {@link #mmm2(String)} instead
   */
  void mmm(String t) {
  }

  void mmm1(int string) {

  }
  void mmm2(String string) {

  }
}