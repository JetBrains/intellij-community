class Foo {

  int takesCharSequence(CharSequence str) {
    return 0;
  }

  int takesChar(char chr) {
    return 0;
  }

  void testOutside() {
    // should be moved
    takesCharSequence(""/*typehere*/);
    takesChar(''/*typehere*/);
  }

  void testInside() {
    // should not be moved
    takesCharSequence("/*typehere*/;")
    takesChar('/*typehere*/;')
  }

  void testInComment() {
    // some comment blabla(/*typehere*/;)
  }
}