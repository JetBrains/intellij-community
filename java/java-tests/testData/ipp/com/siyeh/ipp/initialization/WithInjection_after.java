public final class InjectionSplit {
    //language=JAVA
    String javaCode<caret>;

    {
        javaCode = """
                public class Foo {
    
                void test() {}
                }
                """;
    }
}
