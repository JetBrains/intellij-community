// "Replace with 'Files.newInputStream'" "true"
import java.io.*;
import java.nio.file.Files;

class Foo {
  void test(boolean b, File f) throws IOException {
    Path p;
    if (b) {
      p = f.toPath();
      try (InputStream is = Files.newInputStream(p)) {
      }
    }
  }
}