import java.io.IOException;
import java.io.InputStream;

class Test {
  void withSuffixInFinally(InputStream stream) {
    try<caret> {
      System.out.println(1);
    } catch (Exception e) {
    } finally {
      try {
        stream.close();
      } catch (IOException e) {
      }
      int x = 10;
      System.out.println(x);
    }
  }

}