// "Add 'catch' clause(s)" "true-preview"
import org.intellij.lang.annotations.Language;

class X {
  @Language("JAVA") String java = """
                class Cls {
                  {
                    try {
                    } catch (Exception e) {
                        <selection><caret>throw new RuntimeException(e);</selection>
                    }
                  }
                }
                """;

}