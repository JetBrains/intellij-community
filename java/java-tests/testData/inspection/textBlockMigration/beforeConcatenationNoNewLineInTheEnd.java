// "Replace with text block" "true"

class TextBlockMigration {

  void concatenation() {
    String foobarbaz = "foo\n" <caret>+
                       "bar\n" +
                       "baz";
  }

}