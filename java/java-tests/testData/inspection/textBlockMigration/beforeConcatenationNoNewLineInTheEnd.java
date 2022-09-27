// "Replace with text block" "true-preview"

class TextBlockMigration {

  void concatenation() {
    String foobarbaz = "foo<caret>\n" +
                       "bar\n" +
                       "baz";
  }

}