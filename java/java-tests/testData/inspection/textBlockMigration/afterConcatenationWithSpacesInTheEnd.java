// "Replace with text block" "true"

class TextBlockMigration {

  void concatenationWithExtraSpaces() {
    String code = """
            <html> \s
              <body>
              </body>
            </html>  """;
  }

}