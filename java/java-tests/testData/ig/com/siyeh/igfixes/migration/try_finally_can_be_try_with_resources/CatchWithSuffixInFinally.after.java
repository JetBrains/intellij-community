import java.io.IOException;
import java.io.InputStream;

class Test {
  void withSuffixInFinally(InputStream stream) {
      try (stream) {
          System.out.println(1);
      } catch (IOException e) {
      } finally {
          int x = 10;
          System.out.println(x);
      }
  }

}