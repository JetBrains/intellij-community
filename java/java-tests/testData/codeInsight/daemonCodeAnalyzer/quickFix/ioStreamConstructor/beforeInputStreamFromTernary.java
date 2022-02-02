// "Replace with 'Files.newInputStream'" "true"
import java.io.*;

class Foo {
  void test(File file, boolean b) {
    try (InputStream is = new FileInpu<caret>tStream(b ? new File("foo") : new File("bar"))) {
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
}