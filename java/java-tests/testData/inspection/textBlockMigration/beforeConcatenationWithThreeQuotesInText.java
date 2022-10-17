// "Replace with text block" "true-preview"

class TextBlockMigration {

  void concatenationWithThreeQuotes() {
    String quotes = "this concatenation contains<caret>\n" +
                    " three quotes\n" +
                    "one after another\n" +
                    "\"" +
                    '"' +
                    "\"";
  }

}