// "Annotate variable 'a' as '@NonNls'" "true-preview"
class Foo {
  public void doTest() {
    String a = new String("te<caret>st");
  }
}
