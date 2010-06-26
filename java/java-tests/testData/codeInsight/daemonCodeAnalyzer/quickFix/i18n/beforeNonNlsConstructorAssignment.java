// "Annotate variable 'a' as @NonNls" "true"
class Foo {
  public void doTest() {
    String a;
     a = new String("te<caret>st");
  }
}
