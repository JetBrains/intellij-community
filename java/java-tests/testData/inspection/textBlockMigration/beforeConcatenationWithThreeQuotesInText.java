// "Replace with text block" "true"

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