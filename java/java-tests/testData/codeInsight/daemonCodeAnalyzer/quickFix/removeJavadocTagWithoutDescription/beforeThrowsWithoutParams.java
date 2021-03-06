// "Remove '@throws' tag" "true"

class Foo {
  /**
   * @param someInt blah-blah-blah
   * @return blah-blah-blah
   * @throws<caret>
   */
  double foo(int someInt) {
    return 3.14;
  }
}
