// "Replace with text block" "true"

class TextBlockMigration {

  void concatenationWithIndentAndLineSplitOnWhitespace() {
    String string = """
            foo
            bar
            baz\040""".indent(2);
  }

}