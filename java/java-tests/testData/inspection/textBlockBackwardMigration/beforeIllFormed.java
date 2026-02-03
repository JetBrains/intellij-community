// "Fix all 'Text block can be replaced with regular string literal' problems in file" "false"

class TextBlockMigration {

  void illFormed() {
    String empty = """<caret> """;
  }
}