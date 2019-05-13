// "Replace method call with Test.nnn" "true"
public class Test {

  void foo() {
    nnn("");
  }

  /**
   * {@link Test#nnn(CharSequence)}
   *
   */
  @Deprecated
  void mmm(String s) {
  }

  void nnn(CharSequence ss) {
  }
}