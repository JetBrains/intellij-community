// "Replace with text block" "true-preview"
class TextBlockMigration {
  void concatenationWithBackslash() {
    String s = "foo\\\n<caret>" +
               "bar\\";
  }
}