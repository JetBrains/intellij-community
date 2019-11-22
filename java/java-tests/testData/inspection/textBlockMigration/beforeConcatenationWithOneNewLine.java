// "Fix all 'Text block can be used' problems in file" "false"

class TextBlockMigration {

  void concatenationWithOneNewLine() {
    String code = "<<caret>html>\n" +
                  "</html>";
  }

}