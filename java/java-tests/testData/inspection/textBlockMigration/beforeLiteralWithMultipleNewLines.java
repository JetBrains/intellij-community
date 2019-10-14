// "Replace with text block" "true"

class TextBlockMigration {

  void literalWithNewLine() {
    String foo = "foo<caret>\nbar\nbaz\n";
  }

}