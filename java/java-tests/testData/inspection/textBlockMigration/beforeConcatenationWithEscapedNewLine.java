// "Replace with text block" "true"

class TextBlockMigration {

  void concatenationWithMultipleNewLines() {
    String text = "This<caret> text should be on the same line as \\n this one\n" +
                  "foo\n" +
                  "bar\n";
  }
}