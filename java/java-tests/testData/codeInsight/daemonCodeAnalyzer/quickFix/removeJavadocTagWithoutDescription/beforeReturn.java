// "Remove '@return' tag" "true-preview"

class Foo {
  /**
   * @param someInt blah-blah-blah
   * @return<caret>
   */
  double foo(int someInt) {
    return 3.14;
  }
}
