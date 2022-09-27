// "Replace with text block" "true-preview"

class TextBlockMigration {

  void concatenationWithIndentAndBlankString() {
    String yaml = """
              key1:
                subKey: val1

              key2: val2\
            """;
  }

}