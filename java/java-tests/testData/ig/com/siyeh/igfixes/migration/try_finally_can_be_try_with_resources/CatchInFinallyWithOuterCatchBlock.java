import java.io.IOException;
import java.io.InputStream;

class Test {
  void testException(InputStream stream) {
    try<caret> {
      System.out.println(1);
    } catch (Exception e) {

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