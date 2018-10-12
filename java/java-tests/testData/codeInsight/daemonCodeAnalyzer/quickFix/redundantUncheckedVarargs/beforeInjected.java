// "Remove 'unchecked' suppression" "false"
public class SampleInjected {
    void foo() {
        @SuppressWarnings({"unu<caret>sed"})
        String java = "import java.util.*;\n" +
                      "class A {" +
                      "   void a(List l) {\n" +
                      "        List<String> ll = l;\n" +
                      "  }\n" +
                      "}";
    }
}
