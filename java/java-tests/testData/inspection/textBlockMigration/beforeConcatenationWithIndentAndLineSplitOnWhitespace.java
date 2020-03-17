// "Replace with text block" "true"

class TextBlockMigration {

  void concatenationWithIndentAndLineSplitOnWhitespace() {
    String string = "  foo<caret>\n" +
                    "  bar\n" +
                    "  baz" +
                    " ";
  }

}