import java.io.IOException;
import java.io.InputStream;

class Test {
  void doubleTry(InputStream stream, InputStream stream2) {
    try<caret> {
      System.out.println(1);
    } finally {
      try {
        stream.close();
      } catch (IOException e) {
      }
      try {
        stream2.close();
      } catch (Exception e) {}
    }
  }
}