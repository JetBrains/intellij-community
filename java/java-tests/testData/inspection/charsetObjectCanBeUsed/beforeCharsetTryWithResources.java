// "Replace with 'StandardCharsets.UTF_8'" "true"

import java.io.*;

class Test {
  void test(String inputFile) {
    try (InputStream inputStream = new FileInputStream(inputFile)) {
      new InputStreamReader(inputStream, "<caret>UTF-8");
    }
    catch (IOException ignored) {
    }
  }
}