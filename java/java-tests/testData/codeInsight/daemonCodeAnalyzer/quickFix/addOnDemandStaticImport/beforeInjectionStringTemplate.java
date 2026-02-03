// "Add on-demand static import for 'java.lang.System'" "true-preview"
import org.intellij.lang.annotations.Language;

public class Hello {
  void test(String name, String message) {
    @Language("JAVA")
    String s = STR."""
                class \{name} {
                    void main() {
                      <caret>System.out.println("\{message}");
                    }
                }""";
  }
}
