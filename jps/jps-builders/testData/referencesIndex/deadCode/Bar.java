public class Bar {
  public static final boolean DEBUG = false;

  public static void m() {
    if (DEBUG) {
      System.out.println("I'm dead");
    } else {
      new java.util.ArrayList();
    }
  }
}