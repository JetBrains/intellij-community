// "Replace with text block" "true-preview"

class TextBlockMigration {

  void spaces() {
    String foobarbaz = "foo<caret>\n" +
                       "bar  \n" +
                       "baz  ";
  }

}