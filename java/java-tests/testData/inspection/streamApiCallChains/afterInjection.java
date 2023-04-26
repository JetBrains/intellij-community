// "Replace with '.keySet().stream()'" "true-preview"

import org.intellij.lang.annotations.Language;

class Test {
  public void testAnyMatch() {
    @Language("JAVA")
    String javaText = "import java.util.*;\n" +
            "\n" +
            "class X {\n" +
            "  void test(Map<String, Integer> map) {\n" +
            "    map.keySet().stream().<caret>filter(x -> !x.isEmpty()).forEach(s -> System.out.println(\"hello\"+s));\n" +
            "  }\n" +
            "}\n";
  }
}