// "Replace with a space" "false"
class X {
  void test() {
    String s = """
      Hello<caret>\s
      World
    """
  }
}