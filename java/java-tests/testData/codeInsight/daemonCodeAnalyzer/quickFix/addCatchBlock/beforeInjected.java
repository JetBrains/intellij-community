// "Add 'catch' clause(s)" "true-preview"
import org.intellij.lang.annotations.Language;

class X {
  @Language("JAVA") String java = """
                class Cls {
                  {
                    try {
                    }<caret>
                  }
                }
                """;

}