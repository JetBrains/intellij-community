// "Replace with 'Files.newInputStream()'" "true-preview"
import java.io.*;
import java.nio.file.Files;

class Foo {
  void test(File file) {
    try (InputStream is = Files.newInputStream(new File("foo").toPath())) {
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
}