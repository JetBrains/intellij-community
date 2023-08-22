// "Replace with 'Files.newInputStream()'" "true-preview"
import java.io.*;

class Foo {
  void test(File file) {
    try (InputStream is = new File<caret>InputStream("foo")) {
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
}