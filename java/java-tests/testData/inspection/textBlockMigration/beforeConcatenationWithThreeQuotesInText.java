// "Replace with text block" "true"

class TextBlockMigration {

  void concatenationWithThreeQuotes() {
    String quotes = "<caret>this concatenation contains\n" +
                    " three quotes\n" +
                    "one after another\n" +
                    "\"" +
                    '"' +
                    "\"";
  }

}