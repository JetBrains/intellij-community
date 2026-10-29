public class a {
    @SuppressWarnings("SillyAssignment")
    @org.intellij.lang.annotations.Language("JAVA")
    String test = "class a {\n" +
            "    public void run(int i) {\n" +
            "        i = <caret>i;\n" +
            "    }\n" +
            "}";
}