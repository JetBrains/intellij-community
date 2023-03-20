// "Replace with 'cs'" "true"
import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;

class Test {
  void test(byte[] bytes, Charset cs) throws UnsupportedEncodingException {
      String string = new String(bytes, 0, 100, cs);
  }
}