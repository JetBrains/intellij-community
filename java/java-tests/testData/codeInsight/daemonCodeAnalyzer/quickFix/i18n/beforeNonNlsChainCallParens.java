// "Annotate method 'test()' as '@NonNls'" "true-preview"
class Foo {
  Foo test(String s) {
    return this;
  }
  
  public boolean doTest() {
    System.out.println((test("Hel<caret>lo")).toString());
  }
}
