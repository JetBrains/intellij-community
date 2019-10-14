// "Replace with text block" "true"

class TextBlockMigration {

  void concatenation() {
    String foobarbaz = "foo<caret>\n" +
                       "bar\n" +
                       "baz";
  }

}