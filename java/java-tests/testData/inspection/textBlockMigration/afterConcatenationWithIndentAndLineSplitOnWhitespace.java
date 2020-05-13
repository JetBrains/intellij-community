// "Replace with text block" "true"

class TextBlockMigration {

  void concatenationWithIndentAndLineSplitOnWhitespace() {
    String string = """
            foo
            bar
            baz\s""".indent(2);
  }

}