// "Replace with 'Files.newInputStream'" "true"
import java.io.*;

class Foo {
  void test(String str) {
    try (InputStream is = new File<caret>InputStream(str)) {
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
}