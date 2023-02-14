// "Remove '@return' tag" "true-preview"

class Foo {
  /**
   * @param someInt blah-blah-blah
   */
  double foo(int someInt) {
    return 3.14;
  }
}
