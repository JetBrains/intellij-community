// "Replace with text block" "false"

class TextBlockMigration {

  void concatenationWithEscapedQuotesWithoutLineBreaks() {
    String div = "<caret><div lang=\"{{interpolation?.here}}\">\n" +
                 "</div>\n";
  }

}