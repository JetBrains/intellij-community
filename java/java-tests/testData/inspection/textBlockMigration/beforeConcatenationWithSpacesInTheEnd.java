// "Replace with text block" "true"

class TextBlockMigration {

  void concatenationWithExtraSpaces() {
    String code = "<html>  <caret>\n" +
                  "  <body>\n" +
                  "  </body>\n" +
                  "</html>  ";
  }

}