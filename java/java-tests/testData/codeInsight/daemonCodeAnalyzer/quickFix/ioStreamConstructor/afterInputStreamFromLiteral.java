// "Replace with 'Files.newInputStream'" "true"
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;

class Foo {
  void test(File file) {
    try (InputStream is = Files.newInputStream(Paths.get("foo"))) {
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
}