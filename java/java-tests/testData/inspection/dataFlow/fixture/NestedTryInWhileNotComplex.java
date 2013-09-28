import java.io.FileInputStream;

public class Bar {
  boolean myShouldAccept = true;

  public void run() {
    while (myShouldAccept) {
      try {
        FileInputStream fs = new FileInputStream("a");
        try {
        }
        finally {
          fs.close();
        }
      }
      catch (Exception ignore) {
      }
    }
  }
}
