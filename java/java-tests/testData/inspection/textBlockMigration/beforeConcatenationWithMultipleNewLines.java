// "Replace with text block" "true"

class TextBlockMigration {

  void concatenationWithMultipleNewLines() {
    String html = "<html>\n<caret>" +
                  "    <body>\n" +
                  "        <p>Hello, world</p>\n" +
                  "    </body>\n" +
                  "</html>\n";
  }
}