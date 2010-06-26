// "Annotate variable 'a' as @NonNls" "true"
class Foo {
  public boolean doTest() {
    String a;
    return "te<caret>st".equals(a)
  }
}
