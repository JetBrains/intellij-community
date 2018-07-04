// "Replace method call with Test.nnn" "true"
class Test<T> {
  static void example() {
    Test<String> t = new Test<>();
    t.m<caret>mm("");
  }

  /**
   * {@link Test#nnn(Object)}
   *
   */
  @Deprecated
  void mmm(T t) {
  }

  void nnn(T t) {

  }
}