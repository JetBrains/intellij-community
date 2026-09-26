// "Replace with text block" "true-preview"
class TextBlockMigration {
  void trailingSpacesNoFinalNewlineIndented() {
    String s = "  foo<caret>\n" +
               "  bar  ";
  }
}