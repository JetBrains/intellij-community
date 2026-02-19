// "Replace with 'Files.newOutputStream()'" "true-preview"
import java.io.*;

class Foo {
  void test(File file) {
    try (OutputStream os = new <caret>FileOutputStream(file)) {
    } catch (IOException e) {
      e.printStackTrace();
    }
  }
}