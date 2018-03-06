// "Replace with 'StandardCharsets.UTF_16'" "true"
import java.io.UnsupportedEncodingException;

class Test {
  static final String UTF16 = "UTF-16";

  void test(byte[] bytes) {
    String string = null;
      string = new String(bytes, java.nio.charset.StandardCharsets.UTF_16);
      if(string.startsWith("Foo")) {
      System.out.println("It's a foo!");
    }
  }
}