// "Replace with text block" "true-preview"

class TextBlockMigration {

  void concatenationWithIndentAndLineSplitOnWhitespace() {
    String string = "  foo<caret>\n" +
                    "  bar\n" +
                    "  baz" +
                    " ";
  }

}