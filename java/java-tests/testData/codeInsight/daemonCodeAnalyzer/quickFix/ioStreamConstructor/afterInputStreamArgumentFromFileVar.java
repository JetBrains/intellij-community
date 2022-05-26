// "Replace with 'Files.newInputStream'" "true"
import java.io.*;
import java.nio.file.Files;

class Foo {
  void test(File file) {
    try (InputStream s = handleIS(Files.newInputStream(file.toPath()))) {
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  InputStream handleIS(InputStream is) {
    return is;
  }
}