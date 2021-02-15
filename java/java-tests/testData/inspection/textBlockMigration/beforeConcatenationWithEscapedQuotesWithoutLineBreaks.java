// "Replace with text block" "true"

class TextBlockMigration {

  void concatenationWithEscapedQuotesWithoutLineBreaks() {
    String div = "<caret><div lang=\"{{interpolation?.here}}\">" + 
                 "</div>";
  }

}