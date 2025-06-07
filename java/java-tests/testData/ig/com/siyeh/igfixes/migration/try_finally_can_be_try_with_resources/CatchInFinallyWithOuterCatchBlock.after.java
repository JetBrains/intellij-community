import java.io.IOException;
import java.io.InputStream;

class Test {
  void testException(InputStream stream) {
      try<caret> (stream) {
          try {
              System.out.println(1);
          } catch (Exception e) {

          }
      } catch (IOException _) {

      }
  }
}