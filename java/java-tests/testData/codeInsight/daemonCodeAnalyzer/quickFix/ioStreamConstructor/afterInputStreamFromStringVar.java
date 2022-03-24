// "Replace with 'Files.newInputStream'" "true"
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;

class Foo {
  void test(String str) {
    try (InputStream is = Files.newInputStream(Paths.get(str))) {
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
}