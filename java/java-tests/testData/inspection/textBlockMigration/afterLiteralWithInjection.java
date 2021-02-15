// "Replace with text block" "true"
import org.intellij.lang.annotations.Language;

class TextBlockMigration {

  void literalWithEscapedQuotes() {
    @Language("JAVA") String s = """
            class Foo {
            }
            """;
  }

}