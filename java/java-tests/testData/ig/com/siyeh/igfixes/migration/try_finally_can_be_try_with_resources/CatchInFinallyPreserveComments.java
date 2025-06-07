import java.io.IOException;
import java.io.InputStream;

class Test {
  void withSuffixInFinally(InputStream stream) {
    try<caret> {
      System.out.println(1); // comment 1
    } catch (Exception e) {
      // comment 2
    } finally {
      // comment 3
      try {
        stream.close(); // comment 4
      } catch (IOException e) {
        // comment 5
      }
      // comment 6
    }
  }

}