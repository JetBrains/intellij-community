// "Remove '@param someInt' tag" "true-preview"

class Foo {
  /**
   * @param someInt<caret>
   * @return blah-blah-blah
   */
  double foo(int someInt) {
    return 3.14;
  }
}
