// "Replace with 'Files.newInputStream'" "true"
import java.io.*;

class Foo {
  void test(File file) {
    try (InputStream is = new FileInpu<caret>tStream(new File("foo"))) {
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
}