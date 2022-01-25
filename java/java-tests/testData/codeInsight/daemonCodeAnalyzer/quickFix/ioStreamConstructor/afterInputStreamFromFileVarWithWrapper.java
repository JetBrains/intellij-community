// "Replace with 'Files.newInputStream'" "true"
import java.io.*;
import java.nio.file.Files;

class Foo {
  void test(File file) {
    try (InputStream is = new BufferedInputStream(Files.newInputStream(file.toPath()))) {
    } catch (IOException e) {
      e.printStackTrace();
    }
  }
}