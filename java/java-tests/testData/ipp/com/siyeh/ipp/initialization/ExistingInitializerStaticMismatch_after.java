public final class Simple {
    static String <caret>hello;

    {
      System.out.println("instance initializer");
    }
    
    static {
        hello = "world";
        System.out.println("Initializer!");
    }
}
