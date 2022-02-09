// "Replace with 'Files.newInputStream'" "true"
import java.io.*;

class Foo {
  void test(File file) {
    try (InputStream is = new BufferedInputStream(new <caret>FileInputStream(file))) {
    } catch (IOException e) {
      e.printStackTrace();
    }
  }
}