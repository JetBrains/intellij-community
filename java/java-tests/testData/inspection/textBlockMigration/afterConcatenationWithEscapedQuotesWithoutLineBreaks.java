// "Replace with text block" "true"

class TextBlockMigration {

  void concatenationWithEscapedQuotesWithoutLineBreaks() {
    String div = """
            <div lang="{{interpolation?.here}}"></div>""";
  }

}