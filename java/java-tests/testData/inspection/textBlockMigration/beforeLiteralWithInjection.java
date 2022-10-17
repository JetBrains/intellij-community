// "Replace with text block" "true-preview"
import org.intellij.lang.annotations.Language;

class TextBlockMigration {

  void literalWithEscapedQuotes() {
    @Language("JAVA") String s = "class Foo {<caret>\n" +
                                 "}\n";
  }

}