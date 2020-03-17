// "Replace with text block" "true"

class TextBlockMigration {

  void concatenationWithSpacesAtTheBeginning() {
    String body = """
            <body>
              <p>
              </p>
            </body>""".indent(2);
  }

}