// "Replace with text block" "true-preview"

class TextBlockMigration {

  void concatenationWithEscapedQuotesWithoutLineBreaks() {
    String div = "<caret><div lang=\"{{interpolation?.here}}\">" + 
                 "</div>";
  }

}