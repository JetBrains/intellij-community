// "Specify UTF-8 charset" "true"
import java.io.*;
import java.nio.charset.StandardCharsets;

class X {
  void test(byte[] bytes) {
    String s = new String(bytes, StandardCharsets.UTF_8)
  }
}