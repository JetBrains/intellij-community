// "Replace with 'StandardCharsets.US_ASCII'" "true"
import java.io.UnsupportedEncodingException;

class Test {
  void test(byte[] bytes) {
    String string = null;
    try {
      string = new String(bytes, "ASC<caret>II");
    }
    catch (UnsupportedEncodingException exception) {
      exception.printStackTrace();
    }
    catch (Throwable t) {
      return;
    }
    if(string.startsWith("Foo")) {
      System.out.println("It's a foo!");
    }
  }
}