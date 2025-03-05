import java.io.IOException;
import java.io.InputStream;

class Test {
  void withSuffixInFinally(InputStream stream) {
      try<caret> (stream) {
          try {
              System.out.println(1); // comment 1
          } catch (Exception e) {
              // comment 2
          }
      } catch (IOException e) {
          // comment 5
      }
      // comment 3
      // comment 4
      // comment 6
  }

}