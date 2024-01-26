// "Add on-demand static import for 'java.lang.System'" "true-preview"
import org.intellij.lang.annotations.Language;

public class Hello {
  void test(String name, String message) {
    @Language("JAVA")
    String s = STR."""
                import static java.lang.System.*;
                
                class \{name} {
                    void main() {
                      out.println("\{message}");
                    }
                }""";
  }
}
