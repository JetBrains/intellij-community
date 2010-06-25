// "Annotate parameter 's' as @NonNls" "true"
class Foo {
  public void doTest() {
    doStringTest("te<caret>st");
  }

  private void doStringTest(String s) {
  }
}
