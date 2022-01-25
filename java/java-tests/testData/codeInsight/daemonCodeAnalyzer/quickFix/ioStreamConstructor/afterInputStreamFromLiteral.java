// "Replace with 'Files.newInputStream'" "true"
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;

class Foo {
  void test(File file) {
    try (InputStream is = Files.newInputStream(Path.of("foo"))) {
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
}