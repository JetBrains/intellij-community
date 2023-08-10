// "Replace with 'cs'" "true"
import java.nio.charset.*;
import java.io.UnsupportedEncodingException;

class X {
  byte[] convert(String str, Charset cs) {
    try {
      return str.getBytes(<caret>cs.name());
    } catch (UnsupportedEncodingException e) {
      throw new RuntimeException(e);
    }
  }
}