// "Replace with text block" "true"

class TextBlockMigration {

  void concatenationWithIndentAndBlankString() {
    String yaml = "  key1:<caret>\n" +
                  "    subKey: val1\n" +
                  "\n" +
                  "  key2: val2";
  }

}