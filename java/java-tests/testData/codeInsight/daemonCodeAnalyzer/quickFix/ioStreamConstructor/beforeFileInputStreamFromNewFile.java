// "Replace with 'Files.newInputStream'" "false"
import java.io.*;

class Foo {
  void test(File file) {
    try (FileInputStream is = new FileInpu<caret>tStream(new File("foo"))) {
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
}