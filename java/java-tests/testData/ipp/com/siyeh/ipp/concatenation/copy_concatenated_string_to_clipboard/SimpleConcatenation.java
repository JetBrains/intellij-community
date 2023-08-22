class SimpleConcatenation {

  String buildHTML(String text) {
    return "<html>\n" +<caret>
           "  <body>\n" + text +
           "  </body>\n" +
           "</html>\n";
  }
}