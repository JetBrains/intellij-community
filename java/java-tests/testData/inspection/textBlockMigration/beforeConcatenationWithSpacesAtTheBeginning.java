// "Replace with text block" "INFORMATION-preview"

class TextBlockMigration {

  void concatenationWithSpacesAtTheBeginning() {
    String body =/*3*/ "  <body>\n<caret>"/*1*/ +
      "    <p>\n" +
      "    </p>\n" +
      "  </body>"/*2*/;/*4*/
  }

}