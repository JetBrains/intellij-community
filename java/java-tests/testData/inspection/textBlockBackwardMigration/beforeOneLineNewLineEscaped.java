// "Replace with regular string literal" "true"

class TextBlockMigration {

  void oneLineNoNewLine() {
    String json = """
                  fo<caret>o\
                  bar
                  """;
  }
}