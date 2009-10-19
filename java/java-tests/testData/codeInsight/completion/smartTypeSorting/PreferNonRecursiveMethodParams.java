class Foo {
  Foo foo(int i, String s) {

  }

  Foo foo(String s) {
    String a;
    int b;
    return foo(<caret>)
  }
}