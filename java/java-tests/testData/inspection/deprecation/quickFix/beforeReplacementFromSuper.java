// "Replace method call with 'Test2.nnn()'" "true-preview"
class Test extends Test2 {
  static void example() {
    Test t = new Test();
    t.m<caret>mm("");
  }

  /**
   * {@link Test2#nnn(String)}
   *
   */
  @Deprecated
  void mmm(String t) {
  }


}

class Test2 {
  void nnn(String t) {

  }
}