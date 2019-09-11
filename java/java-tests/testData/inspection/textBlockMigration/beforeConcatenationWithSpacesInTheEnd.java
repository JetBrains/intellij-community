// "Replace with text block" "true"

class TextBlockMigration {

  void concatenationWithExtraSpaces() {
    String code = "<<caret>html>  \n" +
                  "  <body>\n" +
                  "  </body>\n" +
                  "</html>  ";
  }

}