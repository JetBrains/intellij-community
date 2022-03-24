// "Replace with 'Files.newInputStream'" "true"
import java.io.*;

class Foo {
  void test(File file) {
    try (InputStream s = handleIS(new Fi<caret>leInputStream(file))) {
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  InputStream handleIS(InputStream is) {
    return is;
  }
}