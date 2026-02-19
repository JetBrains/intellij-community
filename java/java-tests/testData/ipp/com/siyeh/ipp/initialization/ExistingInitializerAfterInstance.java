public final class Simple {
    static String <caret>hello = "world";
    
    String instanceField = "  hello world ".trim() + hello;
    
    static {
      System.out.println("Initializer!");
    }
}
