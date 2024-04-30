import org.intellij.lang.annotations.Language;

public class InInjection {
  void test() {
    @Language("Java")
    String s = STR."""
        class NewJavaClass {
            void foo(String name) {
                // Highlighting is misplaced -- see IDEA-347183
                String s = "\\<warning descr="'\'' is unnecessarily escaped">b\</warning>\'\\{}";
            }
        }
        """;
  }
}