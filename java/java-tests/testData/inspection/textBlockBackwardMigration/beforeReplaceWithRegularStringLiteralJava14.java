// "Replace with regular string literal" "true"
class TextBlockMigration {
  String sisyphus() {
    return """
                  allow<caret> migrating to regular string literal on language level that does not support text blocks""";
  }
}