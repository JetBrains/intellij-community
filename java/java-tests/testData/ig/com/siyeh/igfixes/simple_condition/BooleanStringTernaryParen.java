class BooleanStringTernaryParen {
  void foo(boolean param) {
    String output = ((<caret>param)) ? ("true") : "false";
  }
}