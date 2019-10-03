// "Replace with text block" "true"

class TextBlockMigration {

  void concatenationWithExtraSpaces() {
    String code = """
            <html>\040\040
              <body>
              </body>
            </html>\040\040""";
  }

}