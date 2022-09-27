// "Replace with text block" "true-preview"

class TextBlockMigration {

  void literalWithEscapedQuotes() {
    String s = """
            {"key": "retracted-but-long", "sentences": ["some long sentence", "another long sentence"], "language":"en-US"}""";
  }

}