import org.intellij.lang.annotations.Language;

class Test {

  @Language("JAVA")
  String block = """
    int a = 1;<caret>
    """;

}