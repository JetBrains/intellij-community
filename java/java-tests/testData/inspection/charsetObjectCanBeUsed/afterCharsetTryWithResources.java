// "Replace with 'StandardCharsets.UTF_8'" "true"

import java.io.*;
import java.nio.charset.StandardCharsets;

class Test {
  void test(String inputFile) {
    try (InputStream inputStream = new FileInputStream(inputFile)) {
      new InputStreamReader(inputStream, StandardCharsets.UTF_8);
    }
    catch (IOException ignored) {
    }
  }
}