import java.io.IOException;
import java.io.InputStream;

class Test {
  void testException(InputStream stream) {
    try<caret> {
      System.out.println(1);
    }
    finally {
      try {
        stream.close();
      }
      catch (IOException _) {

      }
    }
  }
}