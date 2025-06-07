import java.io.IOException;
import java.io.InputStream;

class Test {
  void testException(InputStream stream) {
      try (stream) {
          System.out.println(1);
      } catch (IOException _) {

      }
  }
}