// "Replace with text block" "true"

class TextBlockMigration {

  void concatenationWithNonStrings() {
    String answer = "T<caret>he answer to the meaning of life,\n" +
                    "the universe,\n" +
                    "and everything\n" +
                    "is "  + 42 + '\n';
  }

}