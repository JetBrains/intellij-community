class Foo {
  String foo = "foo";
  public Foo(int foo) {
  }
  
  void bar() {
    String fo<caret>oBar = foo.subString(1);
  }
}