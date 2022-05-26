// "Replace with 'Files.newInputStream'" "false"
import java.io.*;

class Foo {
  void test(File file) {
    try (InputStream s = handleFIS((new Fi<caret>leInputStream(file)))) {
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  InputStream handleFIS(FileInputStream fis) {
    return fis;
  }
}