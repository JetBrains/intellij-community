import org.intellij.lang.annotations.Language;
import org.intellij.lang.annotations.Subst;

public class Hello {

  @Language("JAVA")
  private static final StringTemplate.Processor<String, RuntimeException> JAVA = STR;

  void test(@Subst("MyClass") String name, String message) {
    String s = JAVA."""
                import static java.lang.System.*;
                class \{name} {
                    private MyClass() {
                    }
                
                    static MyClass createMyClass() {
                        return new MyClass();
                    }
                
                    void main(int x) {
                        if (x > 0) {
                            out.println("\{message}");
                        }
                        if (x > 0) {
                            out.println("Hello");
                        }
                    }
                }""";
  }
}
