// "Replace with 'StandardCharsets.UTF-8'" "false"
package java.net;
import java.io.UnsupportedEncodingException;
import java.nio.charset.*;

class Test {
  String test(String uri, String text) throws UnsupportedEncodingException {
    return uri + "&test=" + URLEncoder.encode(text, "UTF<caret>-8");
  }
}

class URLEncoder {

  static String encode(String text, String encoding) {
    return text;
  }

  static String encode(String text, Charset charset) {
    return text;
  }

}