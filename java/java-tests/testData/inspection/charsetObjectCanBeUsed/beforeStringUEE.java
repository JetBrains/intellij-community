// "Replace with 'StandardCharsets.UTF_16'" "true"
import java.io.UnsupportedEncodingException;

class Test {
  static final String UTF16 = "UTF-16";

  void test(byte[] bytes) {
    String string = null;
    try {
      string = new String(bytes, UTF<caret>16);
    }
    catch (UnsupportedEncodingException exception) {
      exception.printStackTrace();
    }
    if(string.startsWith("Foo")) {
      System.out.println("It's a foo!");
    }
  }
}