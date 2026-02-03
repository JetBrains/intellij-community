public final class Simple {
    static String <caret>hello;
    
    String instanceField = "  hello world ".trim() + hello;
    
    static {
        hello = "world";
        System.out.println("Initializer!");
    }
}
