// "Fix all 'Text block can be used' problems in file" "false"

class TextBlockMigration {

  void concatenationWithTextBlock() {
    String concat = "foo\n<caret>" + "bar\n" +
                    """
                    baz""";
  }

}