// "Replace with text block" "true-preview"

class TextBlockMigration {

  void concatenationWithMultipleNewLines() {
    String text = """
            This text should be on the same line as \\n this one
            foo
            bar
            """;
  }
}