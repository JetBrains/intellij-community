// "Replace with 'Files.newOutputStream'" "true"
import java.io.*;
import java.nio.file.Files;

class Foo {
  void test(File file) {
    try (OutputStream os = Files.newOutputStream(file.toPath())) {
    } catch (IOException e) {
      e.printStackTrace();
    }
  }
}