// "Replace method call with Test2.nnn" "true"
class Test extends Test2 {
  static void example() {
    Test t = new Test();
    t.nnn("");
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