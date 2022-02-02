// "Replace with 'Files.newInputStream'" "true"
import java.io.*;

class Foo {
  void test(boolean b, File f) throws IOException {
    Path p;
    if (b) {
      p = f.toPath();
      try (InputStream is = new FileI<caret>nputStream(f)) {
      }
    }
  }
}