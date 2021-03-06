// "Annotate variable 'a' as @NonNls" "true"
class Foo {
  public void doTest() {
    String a = (this.s("t<caret>est"));
  }
  
  String s(String s) {
    return s;
  }
}
