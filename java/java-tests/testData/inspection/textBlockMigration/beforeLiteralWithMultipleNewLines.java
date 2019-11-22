// "Replace with text block" "true"

class TextBlockMigration {

  void literalWithNewLine() {
    String foo = "foo\nbar<caret>\nbaz\n";
  }

}