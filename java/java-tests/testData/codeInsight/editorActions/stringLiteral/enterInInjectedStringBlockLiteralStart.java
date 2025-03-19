import org.intellij.lang.annotations.Language;

class Test {

  @Language("JAVA")
  String block = """
    <caret>int a = 1;
    """;

}