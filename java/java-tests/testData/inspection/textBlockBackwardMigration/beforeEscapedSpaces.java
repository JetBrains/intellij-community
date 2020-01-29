// "Replace with regular string literal" "true"

class TextBlockMigration {

  void escapeSpaces() {
    String block = """
        text\040\040\
        block \s<caret>
        \s""";
  }

}