// "Replace with 'StandardCharsets.US_ASCII'" "true"
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;

class Test {
  void test(byte[] bytes) {
    String string = null;
    try {
      string = new String(bytes, StandardCharsets.US_ASCII);
    } catch (Throwable t) {
      return;
    }
    if(string.startsWith("Foo")) {
      System.out.println("It's a foo!");
    }
  }
}