// "Fix all 'Text block can be used' problems in file" "false"

class NonStringLiteral {

  void empty() {
    String textBlock = """<caret>
      what?
      """;
  }

}