// "Replace with regular string literal" "true"

class TextBlockMigration {

  void oneLineNoNewLine() {
    String json = """
                  console.lo<caret>g("hello!");""";
  }
}