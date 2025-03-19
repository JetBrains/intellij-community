import java.io.IOException;

class C {
  private static void throwStuff() throws IOException {}

  public static void main(String[] args) {
    try {
      throwStuff();
    } catch (NumberFormatException nfe) {
      System.out.println("ERROR 2:" + nfe.getMessage());
      System.exit(1);
    } catch (IOException | IllegalArgumentException ioe) {
      System.out.println("ERROR:" + ioe.getMessage());
      System.exit(1);
    }
  }
}