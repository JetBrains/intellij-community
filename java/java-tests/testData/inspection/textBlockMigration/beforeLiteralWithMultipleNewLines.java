// "Replace with text block" "true-preview"

class TextBlockMigration {

  void literalWithNewLine() {
    String foo = "foo<caret>\nbar\nbaz\n";
  }

}