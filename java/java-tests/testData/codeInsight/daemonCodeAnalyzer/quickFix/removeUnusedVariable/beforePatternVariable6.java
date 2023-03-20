// "Remove pattern variable" "false"
class X {
  public void test(Object object) {
    switch (object) {
      case String string<caret> -> {}
      default -> {}
    }
  }
}