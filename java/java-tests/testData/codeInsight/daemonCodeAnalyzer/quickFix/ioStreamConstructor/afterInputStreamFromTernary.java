// "Replace with 'Files.newInputStream'" "true"
import java.io.*;
import java.nio.file.Files;

class Foo {
  void test(File file, boolean b) {
    try (InputStream is = Files.newInputStream((b ? new File("foo") : new File("bar")).toPath())) {
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
}