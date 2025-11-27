public final class Simple {
    static String <caret>hello = "world";

    {
      System.out.println("instance initializer");
    }
    
    static {
      System.out.println("Initializer!");
    }
}
