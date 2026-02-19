import java.io.IOException;

class C {
  private static void throwStuff() throws IOException {}

  public static void main(String[] args) {
    try {
      throwStuff();
    } catch (IOException ioe) {
      System.out.println("ERROR:" + ioe.getMessage());
      System.exit(1);
    } catch (NumberFormatException nfe) {
      System.out.println("ERROR 2:" + nfe.getMessage());
      System.exit(1);
    } <warning descr="'catch' branch identical to 'IOException' branch">catch (IllegalArgumentException i<caret>ae)</warning> {
      System.out.println("ERROR:" + iae.getMessage());
      System.exit(1);
    }
  }
}