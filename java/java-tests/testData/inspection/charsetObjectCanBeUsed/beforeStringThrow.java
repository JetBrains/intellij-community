// "Replace with 'StandardCharsets.ISO_8859_1'" "true"
import java.io.UnsupportedEncodingException;

class Test {
  void test(byte[] bytes) throws UnsupportedEncodingException {
    String string = new String(bytes, 0, 100, "ISO-8859<caret>-1");
    if(string.startsWith("Foo")) {
      System.out.println("It's a foo!");
    }
  }
}