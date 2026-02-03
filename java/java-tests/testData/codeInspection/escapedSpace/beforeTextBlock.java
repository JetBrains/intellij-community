// "Replace '\s' sequences with spaces" "false"
class X {
  void test() {
    String s = """
      Hello<caret>\s
      World
    """
  }
}