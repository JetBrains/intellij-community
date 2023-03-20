// "Replace with text block" "true-preview"

class TextBlockMigration {

  void concatenationWithEscapedQuotesWithoutLineBreaks() {
    String div = """
            <div lang="{{interpolation?.here}}"></div>""";
  }

}