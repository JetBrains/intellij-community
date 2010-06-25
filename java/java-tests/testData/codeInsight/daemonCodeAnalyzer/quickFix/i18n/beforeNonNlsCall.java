// "Annotate variable 'a' as @NonNls" "true"
class Foo {
  public boolean doTest() {
    String a;
    return a.startsWith("te<caret>st");
  }
}
