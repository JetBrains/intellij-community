// "Replace with regular string literal" "true"

class TextBlockMigration {

  void escapeSequence() {
    String oneLineTextBlock = "\"this is one \"line\"\n text block\134\\\"\n";
  }

}