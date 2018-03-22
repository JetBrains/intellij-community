// "Replace with 'StandardCharsets.ISO_8859_1'" "true"
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;

class Test {
  void test(byte[] bytes) throws UnsupportedEncodingException {
    String string = new String(bytes, 0, 100, StandardCharsets.ISO_8859_1);
    if(string.startsWith("Foo")) {
      System.out.println("It's a foo!");
    }
  }
}