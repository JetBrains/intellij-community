// "Replace with text block" "true"

class TextBlockMigration {

  void concatenationWithSpacesAtTheBeginning() {
    String body = "  <body>\n<caret>" +
      "    <p>\n" +
      "    </p>\n" +
      "  </body>";
  }

}