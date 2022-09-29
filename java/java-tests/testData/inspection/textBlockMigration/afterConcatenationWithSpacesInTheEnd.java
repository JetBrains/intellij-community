// "Replace with text block" "true-preview"

class TextBlockMigration {

  void concatenationWithExtraSpaces() {
    String code = """
            <html> \s
              <body>
              </body>
            </html> \s""";
  }

}