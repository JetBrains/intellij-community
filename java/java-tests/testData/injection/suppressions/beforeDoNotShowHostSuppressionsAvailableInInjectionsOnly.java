public class a {
    {
        doMemberTest("    public void run(int i) {\n" +
                "        i = <caret>i;\n" +
                "    }");
    }
    
    static void doMemberTest(@org.intellij.lang.annotations.Language(value = "JAVA", prefix = "class A {", suffix = "}") String s);
}