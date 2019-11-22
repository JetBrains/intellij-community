// "Replace with regular string literal" "true"

class TextBlockMigration {

  void oneLine() {
    String json = """
                  console.lo<caret>g("hello!");
                  """;
  }
}