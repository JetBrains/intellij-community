// "Replace with 'StandardCharsets.UTF_16'" "true"
import java.io.UnsupportedEncodingException;

class Test {
  void test(byte[] bytes) {
    String string = null;
    try {
      string = new String(bytes, java.nio.charset.StandardCharsets.UTF_16);
    } catch (Throwable t) {
      return;
    }
    if(string.startsWith("Foo")) {
      System.out.println("It's a foo!");
    }
  }
}