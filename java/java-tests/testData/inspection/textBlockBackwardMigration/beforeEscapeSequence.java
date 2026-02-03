// "Replace with regular string literal" "true"

class TextBlockMigration {

  void escapeSequence() {
    String oneLineTextBlock = """
        \u005c"this is one "line"\n\s\
        text <caret>block\134\u005c\\"
        """;
  }

}