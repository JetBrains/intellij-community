// "Replace method call with 'Test.nnn()'" "true-preview"
public class Test {

  void foo() {
    m<caret>mm("");
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