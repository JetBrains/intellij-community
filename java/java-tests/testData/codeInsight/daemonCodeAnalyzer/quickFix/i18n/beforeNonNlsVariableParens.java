// "Annotate variable 'a' as '@NonNls'" "true-preview"
class Foo {
  public void doTest() {
    String a = (s("t<caret>est"));
  }
  
  String s(String s) {
    return s;
  }
}
