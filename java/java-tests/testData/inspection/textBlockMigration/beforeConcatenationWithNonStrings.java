// "Replace with text block" "true-preview"

class TextBlockMigration {

  void concatenationWithNonStrings() {
    String answer = "The answer to the meaning of life,<caret>\n" +
                    "the universe,\n" +
                    "and everything\n" +
                    "is "  + 42 + '\n';
  }

}