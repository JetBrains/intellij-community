import org.jetbrains.annotations.NotNull;

public class CodeFlowTest {
  public static void main (String[] args) {
    String string;
    Exception exception;
    try {
      string = getString();
      exception = null;
    } catch (SomeException e1) {
      string = null;
      exception = e1;
    }

    if (string != null)
      System.out.println ("Not null");
    else
      exception.printStackTrace();
  }

  @NotNull 
  private static String getString () throws SomeException {
    if (Math.random() < 0.5)
      throw new SomeException();
    else
      return "";
  }

  private static class SomeException extends Exception {
  }
}
