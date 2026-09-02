// "Add 'StandardCharsets.UTF_8' argument" "true-preview"
import java.io.*;
import java.nio.charset.StandardCharsets;

class X {
  void test(String s) {
    byte[] bytes = s.getBytes(StandardCharsets.UTF_8);
  }
}