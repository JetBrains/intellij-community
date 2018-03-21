// "Replace with 'StandardCharsets.UTF_16'" "true"
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;

class Test {
  static final String UTF16 = "UTF-16";

  void test(byte[] bytes) {
    String string = null;
      string = new String(bytes, StandardCharsets.UTF_16);
      if(string.startsWith("Foo")) {
      System.out.println("It's a foo!");
    }
  }
}