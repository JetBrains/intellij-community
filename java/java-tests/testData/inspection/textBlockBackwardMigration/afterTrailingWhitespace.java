// "Replace with regular string literal" "true"

class TextBlockMigration {

  String multipleLiterals() {
    return "hello \t\f\n" +
           "world \t\f\n";
  }
}