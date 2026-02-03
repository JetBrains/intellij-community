// "Replace with 'StandardCharsets.UTF_16'" "true"
import java.io.UnsupportedEncodingException;

class Test {
  static final String UTF16 = "UTF-16";

  void test(byte[] bytes) {
    String string = null;
    String string2 = null;
    try {
      string = new String(bytes, UTF<caret>16);
      string2 = new String(bytes, "UTF-8");
    }
    // catch is still necessary after the single replacement
    catch (UnsupportedEncodingException exception) {
      exception.printStackTrace();
    }
    if(string.startsWith("Foo")) {
      System.out.println("It's a foo!");
    }
  }
}