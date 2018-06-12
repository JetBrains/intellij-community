// "Replace with 'StandardCharsets.UTF_16'" "false"
import java.io.UnsupportedEncodingException;

class Test {
  static final String MY_ENCODING = "UTF-16";

  void test(byte[] bytes) {
    String string = null;
    try {
      // Do not suggest the replacement as "MY_ENCODING" could be a tuneable constant
      string = new String(bytes, MY_ENCO<caret>DING);
    }
    catch (UnsupportedEncodingException exception) {
      exception.printStackTrace();
    }
    if(string.startsWith("Foo")) {
      System.out.println("It's a foo!");
    }
  }
}