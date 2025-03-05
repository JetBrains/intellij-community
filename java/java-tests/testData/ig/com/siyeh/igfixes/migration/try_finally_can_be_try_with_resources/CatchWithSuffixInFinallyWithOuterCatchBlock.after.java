import java.io.IOException;
import java.io.InputStream;

class Test {
  void withSuffixInFinally(InputStream stream) {
      try<caret> (stream) {
          try {
              System.out.println(1);
          } catch (Exception e) {
          }
      } catch (IOException e) {
      } finally {
          int x = 10;
          System.out.println(x);
      }
  }

}