// "Fix all 'Text block can be used' problems in file" "false"

class TextBlockMigration {

  void literalWithNewLine() {
    String foo = "foo<caret>\nbar";
  }

}