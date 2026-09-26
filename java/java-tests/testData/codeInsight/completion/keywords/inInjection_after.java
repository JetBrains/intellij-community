import org.intellij.lang.annotations.Language;

class Hello {
  void test() {
    @Language("JAVA") String string = """
           public <caret>
           """;
  }
}
