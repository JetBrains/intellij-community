// "Replace with 'Files.newInputStream'" "true"
import java.io.*;

class Foo {
  void test(File file) {
    try (InputStream is = new FileInputStr<caret>eam(file)) {
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
}