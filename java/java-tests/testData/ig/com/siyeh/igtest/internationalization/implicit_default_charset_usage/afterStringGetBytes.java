// "Specify UTF-8 charset" "true"
import java.io.*;
import java.nio.charset.StandardCharsets;

class X {
  void test(String s) {
    byte[] bytes = s.getBytes(StandardCharsets.UTF_8);
  }
}