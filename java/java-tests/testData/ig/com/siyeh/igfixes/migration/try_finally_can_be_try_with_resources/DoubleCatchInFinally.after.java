import java.io.IOException;
import java.io.InputStream;

class Test {
  void doubleTry(InputStream stream, InputStream stream2) {
      try<caret> (stream; stream2) {
          System.out.println(1);
      } catch (IOException e) {
      } catch (Exception e) {
      }
  }
}