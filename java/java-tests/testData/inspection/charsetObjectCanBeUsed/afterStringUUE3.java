// "Replace with 'cs'" "true"
import java.nio.charset.*;
import java.io.UnsupportedEncodingException;

class X {
  byte[] convert(String str, Charset cs) {
      return str.getBytes(cs);
  }
}