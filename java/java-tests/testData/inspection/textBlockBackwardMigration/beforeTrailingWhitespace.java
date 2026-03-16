// "Replace with regular string literal" "true"

class TextBlockMigration {

  String multipleLiterals() {
    return """<caret>
                 hello \t\f
                 world \t\f
                 """;
  }
}